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

private const val BACKOFF_START_MS = 5_000L
private const val BACKOFF_MAX_MS = 5 * 60_000L

class EventbaseClient internal constructor(
    private val config: EventbaseConfig,
    private val storage: Storage,
    private val sink: Sink,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val queue = EventQueue(storage, clock)
    private val mutex = Mutex()

    @Volatile
    private var userId: String? = storage.get(KEY_USER)
    private var backoffUntil = 0L
    private var backoff = BACKOFF_START_MS

    val installId: String = storage.get(KEY_INSTALL) ?: newId().also { storage.put(KEY_INSTALL, it) }
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
        // props 复制一份：调用方之后改自己的 map 不能影响已入队的事件
        queue.add(QueuedEvent(event.name, clock.now(), flow, event.props.toMap(), sessionId, userId))
        if (config.logEvents) log(event)
        if (queue.size >= config.flushAt) scope.launch { flush() }
    }

    fun setUserId(id: String?) {
        userId = id
        if (id == null) storage.remove(KEY_USER) else storage.put(KEY_USER, id)
    }

    fun clearUserId() = setUserId(null)

    /** 落盘保存，进程被杀也能把回跳后的事件接回同一条漏斗。 */
    fun startFlow(): String = newId().also { storage.put(KEY_FLOW, it) }

    fun currentFlow(): String? = storage.get(KEY_FLOW)

    fun endFlow() = storage.remove(KEY_FLOW)

    suspend fun flush() {
        mutex.withLock {
            if (clock.now() < backoffUntil) return
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
                    }
                    SendResult.RETRY -> {
                        backoffUntil = clock.now() + backoff
                        backoff = minOf(backoff * 2, BACKOFF_MAX_MS)
                        return
                    }
                }
            }
        }
    }

    internal fun queued(): List<QueuedEvent> = queue.snapshot()

    private fun log(event: Event) = println("[eventbase] ${event.name} ${event.props}")
}

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

internal fun defaultScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
