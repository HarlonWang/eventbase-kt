package wang.harlon.eventbase

import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope

/** 全局门面。多 App 共用同一套接入形态：init 一次，之后到处 track。 */
object Eventbase {
    @Volatile
    private var client: EventbaseClient? = null

    val current: EventbaseClient?
        get() = client

    fun init(
        config: EventbaseConfig,
        storage: Storage,
        httpClient: HttpClient = HttpClient(),
        clock: Clock = systemClock(),
        scope: CoroutineScope = defaultScope(),
    ): EventbaseClient = install { EventbaseClient(config, storage, HttpSink(httpClient), clock, scope) }

    /** 测试入口：事件只进 sink，不发网络。 */
    fun initForTest(
        sink: Sink,
        config: EventbaseConfig = testConfig(),
        storage: Storage = MemoryStorage(),
        clock: Clock = systemClock(),
        scope: CoroutineScope = defaultScope(),
    ): EventbaseClient = install { EventbaseClient(config, storage, sink, clock, scope) }

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
        client = null
    }

    /**
     * **先到先得**：已装好就返回既有实例，不构造第二个。两个 EventbaseClient 各持一份内存队列
     * 却共用同一份 Storage，`persist()` 会互相覆盖，直接丢事件。测试用 [reset] 解除。
     */
    private fun install(create: () -> EventbaseClient): EventbaseClient {
        client?.let { return it }
        return create().also { client = it }
    }
}

private fun testConfig() =
    EventbaseConfig(endpoint = "https://test.invalid/t", appKey = "test", appVersion = "0", platform = "test")
