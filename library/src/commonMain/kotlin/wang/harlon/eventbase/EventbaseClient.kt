package wang.harlon.eventbase

import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val KEY_INSTALL = "eventbase.install"
private const val KEY_USER = "eventbase.user"
private const val KEY_FLOW = "eventbase.flow"

/** 服务端对超过这个键数、或有键长超过 [MAX_KEY_LENGTH] 的事件整条丢弃（服务端仓 docs/protocol.md「限制」） */
internal const val MAX_PROPS = 20
internal const val MAX_KEY_LENGTH = 40

private const val BACKOFF_START_MS = 5_000L
private const val BACKOFF_MAX_MS = 5 * 60_000L

class EventbaseClient internal constructor(
    private val config: EventbaseConfig,
    private val storage: Storage,
    private val sink: Sink,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val onDispose: () -> Unit = {},
) {
    private val queue = EventQueue(storage, clock)
    private val mutex = Mutex()

    @Volatile
    private var userId: String? = storage.get(KEY_USER)

    @Volatile
    private var properties: Map<String, Any> = emptyMap()
    private val propertiesLock = createLock()
    private var backoffUntil = 0L
    private var backoff = BACKOFF_START_MS

    val installId: String =
        storage.get(KEY_INSTALL) ?: (config.installId ?: newId()).also { storage.put(KEY_INSTALL, it) }
    val sessionId: String = newId()

    /** 首个 Activity 之前就有事件 = 这个进程是被后台任务拉起来的 */
    @Volatile
    internal var trackedAnything = false
        private set

    internal val lifecycle: LifecycleTracker by lazy {
        LifecycleTracker(this, clock) { scope.launch { flush() } }
    }

    fun track(event: Event, flow: String? = null) {
        trackedAnything = true
        val props = withProperties(event.name, canonicalProps(event.props))
        queue.add(QueuedEvent(newId(), event.name, clock.now(), flow, props, sessionId, userId))
        if (config.logEvents) logLine("track ${event.name} $props")
        if (queue.size >= config.flushAt) scope.launch { flush() }
    }

    fun setUserId(id: String?) {
        userId = id
        if (id == null) storage.remove(KEY_USER) else storage.put(KEY_USER, id)
    }

    fun clearUserId() = setUserId(null)

    /**
     * 此后每条事件都带上 [key]（入队时合并，事件自己的同名属性优先）；[value] 为 null 即移除。
     * 只在本进程内有效，不落盘。合并后超过 [MAX_PROPS] 个键的事件不附加任何全局属性；
     * 空键或超过 [MAX_KEY_LENGTH] 的键不接受——它会让此后每条事件都被服务端丢弃。
     */
    fun setProperty(key: String, value: Any?) {
        if (key.isEmpty() || key.length > MAX_KEY_LENGTH) {
            if (config.logEvents) logLine("setProperty ignored: key \"$key\" must be 1..$MAX_KEY_LENGTH characters")
            return
        }
        propertiesLock.withLock {
            properties = if (value == null) properties - key else properties + canonicalProps(mapOf(key to value))
        }
    }

    fun removeProperty(key: String) = setProperty(key, null)

    /** 落盘保存，进程被杀也能把回跳后的事件接回同一条漏斗。 */
    fun startFlow(): String = newId().also { storage.put(KEY_FLOW, it) }

    fun currentFlow(): String? = storage.get(KEY_FLOW)

    fun endFlow() = storage.remove(KEY_FLOW)

    suspend fun flush() {
        mutex.withLock {
            if (clock.now() < backoffUntil) {
                if (config.logEvents) logLine("flush skipped, backoff for ${backoffUntil - clock.now()}ms")
                return
            }
            // 外层兜落盘失败（peek 清过期、drop 重写队列）：漏出去会顺着 scope.launch 崩掉宿主。
            // 内层不能并进来——发送失败要就地转 RETRY，"flush N kept" 那行诊断才准
            try {
                while (true) {
                    val events = queue.peek()
                    if (events.isEmpty()) return
                    val head = events.first()
                    val batch = Batch(installId, head.session, head.user, config, events)
                    val result = try {
                        sink.send(batch)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        SendResult.RETRY
                    }
                    when (result) {
                        SendResult.DROP -> {
                            queue.drop(events.size)
                            backoff = BACKOFF_START_MS
                            if (config.logEvents) logLine("flush ${events.size} sent, queued=${queue.size}")
                        }
                        SendResult.RETRY -> {
                            backoffUntil = clock.now() + backoff
                            if (config.logEvents) logLine("flush ${events.size} kept, retry in ${backoff}ms, queued=${queue.size}")
                            backoff = minOf(backoff * 2, BACKOFF_MAX_MS)
                            return
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                backoffUntil = clock.now() + backoff
                if (config.logEvents) logLine("flush aborted on storage failure, retry in ${backoff}ms, queued=${queue.size}")
                backoff = minOf(backoff * 2, BACKOFF_MAX_MS)
            }
        }
    }

    internal fun queued(): List<QueuedEvent> = queue.snapshot()

    internal fun dispose() = onDispose()

    private fun withProperties(name: String, own: Map<String, Any>): Map<String, Any> {
        val extra = properties.filterKeys { it !in own }
        if (extra.isEmpty()) return own
        if (own.size + extra.size > MAX_PROPS) {
            if (config.logEvents) logLine("track $name: global properties ${extra.keys} not attached, ${own.size} own + ${extra.size} would exceed $MAX_PROPS keys")
            return own
        }
        return own + extra
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

internal fun defaultScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
