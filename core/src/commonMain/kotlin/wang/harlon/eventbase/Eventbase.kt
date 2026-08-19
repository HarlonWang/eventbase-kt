package wang.harlon.eventbase

import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope

/** 全局门面。多 App 共用同一套接入形态：init 一次，之后到处 track。 */
object Eventbase {
    private val lock = Lock()

    @Volatile
    private var client: EventbaseClient? = null

    val current: EventbaseClient?
        get() = client

    fun init(
        config: EventbaseConfig,
        storage: Storage,
        httpClient: HttpClient? = null,
        clock: Clock = systemClock(),
        scope: CoroutineScope = defaultScope(),
    ): EventbaseClient = installClient(config, storage, httpClient, clock, scope).client

    /** 测试入口：事件只进 sink，不发网络。 */
    fun initForTest(
        sink: Sink,
        config: EventbaseConfig = testConfig(),
        storage: Storage = MemoryStorage(),
        clock: Clock = systemClock(),
        scope: CoroutineScope = defaultScope(),
    ): EventbaseClient = install { EventbaseClient(config, storage, sink, clock, scope) }.client

    fun track(event: Event, flow: String? = null) {
        client?.track(event, flow)
    }

    fun setUserId(id: String?) {
        client?.setUserId(id)
    }

    fun clearUserId() {
        client?.clearUserId()
    }

    fun startFlow(): String? = client?.startFlow()

    fun currentFlow(): String? = client?.currentFlow()

    suspend fun flush() {
        client?.flush()
    }

    /** 平台侧生命周期回调的入口，App 代码不必直接调。 */
    fun onForeground() {
        client?.lifecycle?.onForeground()
    }

    fun onBackground() {
        client?.lifecycle?.onBackground()
    }

    fun reset() {
        lock.withLock { client = null }
    }

    /**
     * **先到先得**，读-建-写在同一把锁内：两个 EventbaseClient 各持一份内存队列却共用
     * 同一份 Storage，`persist()` 会互相覆盖、直接丢事件。[Installed.isNew] 让平台侧
     * 能恰好注册一次生命周期观察者。
     */
    internal fun install(create: () -> EventbaseClient): Installed = lock.withLock {
        client?.let { return@withLock Installed(it, isNew = false) }
        val created = create()
        client = created
        Installed(created, isNew = true)
    }
}

internal data class Installed(val client: EventbaseClient, val isNew: Boolean)

/** 默认 HttpClient 在锁内、确认要建实例时才构造——重复 init 不该造出一个没人 close 的客户端。 */
internal fun installClient(
    config: EventbaseConfig,
    storage: Storage,
    httpClient: HttpClient?,
    clock: Clock = systemClock(),
    scope: CoroutineScope = defaultScope(),
): Installed = Eventbase.install {
    EventbaseClient(config, storage, HttpSink(httpClient ?: HttpClient()), clock, scope)
}

private fun testConfig() =
    EventbaseConfig(endpoint = "https://test.invalid/t", appKey = "test", appVersion = "0", platform = "test")
