package wang.harlon.eventbase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val KEY = "eventbase.queue"

/**
 * 落盘队列。溢出丢最老、出队前丢弃过期事件——**自清窗口必须与服务端的拒收窗口同一个数**，
 * 否则客户端死攥老事件攒到最后也是被拒，白占队列白耗电。
 *
 * 所有状态变更走 [lock]：track 在调用方线程入队，flush 在后台协程出队。
 */
internal class EventQueue(private val storage: Storage, private val clock: Clock) {
    private val lock = Lock()
    private val items = ArrayDeque<QueuedEvent>()

    init {
        storage.get(KEY)?.let { items.addAll(decode(it)) }
    }

    val size: Int get() = lock.withLock { items.size }

    fun add(event: QueuedEvent) {
        lock.withLock {
            items.addLast(event)
            while (items.size > Limits.QUEUE_CAP) items.removeFirst()
            persist()
        }
    }

    /**
     * 取一批待发事件，同时把过期的清掉。
     * 一批只取 [session] 与 [user] 相同的连续前缀——两者是批级字段，混批会错误归因。
     */
    fun peek(limit: Int = Limits.BATCH): List<QueuedEvent> = lock.withLock {
        purgeExpired()
        val head = items.firstOrNull() ?: return@withLock emptyList()
        items.asSequence()
            .take(limit)
            .takeWhile { it.session == head.session && it.user == head.user }
            .toList()
    }

    fun drop(count: Int) {
        lock.withLock {
            repeat(minOf(count, items.size)) { items.removeFirst() }
            persist()
        }
    }

    fun snapshot(): List<QueuedEvent> = lock.withLock { items.toList() }

    /** 全量过滤而非只清队头：时钟没有单调性保证，回拨后过期事件可能排在新事件之后。 */
    private fun purgeExpired() {
        val cutoff = clock.now() - Limits.MAX_AGE_MS
        if (items.none { it.at < cutoff }) return
        val kept = items.filter { it.at >= cutoff }
        items.clear()
        items.addAll(kept)
        persist()
    }

    private fun persist() {
        if (items.isEmpty()) storage.remove(KEY) else storage.put(KEY, encode(items))
    }
}

private val json = Json { ignoreUnknownKeys = true }

internal fun encode(events: Collection<QueuedEvent>): String =
    buildJsonArray {
        events.forEach { event ->
            add(
                buildJsonObject {
                    put("name", JsonPrimitive(event.name))
                    put("at", JsonPrimitive(event.at))
                    event.flow?.let { put("flow", JsonPrimitive(it)) }
                    put("session", JsonPrimitive(event.session))
                    event.user?.let { put("user", JsonPrimitive(it)) }
                    put("props", encodeProps(event.props))
                }
            )
        }
    }.toString()

internal fun encodeProps(props: Map<String, Any?>): JsonObject =
    buildJsonObject {
        props.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> put(key, JsonPrimitive(value))
                is Number -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                is Enum<*> -> put(key, JsonPrimitive(value.name.lowercase()))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }

/** 逐条解析：一条坏数据只丢它自己，不能带走整个队列。 */
private fun decode(raw: String): List<QueuedEvent> {
    val array = runCatching { json.parseToJsonElement(raw) as JsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element -> runCatching { decodeOne(element.jsonObject) }.getOrNull() }
}

private fun decodeOne(obj: JsonObject): QueuedEvent =
    QueuedEvent(
        name = obj.getValue("name").jsonPrimitive.content,
        at = obj.getValue("at").jsonPrimitive.long(),
        flow = obj["flow"]?.jsonPrimitive?.content,
        props = obj["props"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.scalar() } ?: emptyMap(),
        session = obj.getValue("session").jsonPrimitive.content,
        user = obj["user"]?.jsonPrimitive?.content,
    )

private fun JsonPrimitive.long(): Long = longOrNull ?: content.toLong()

private fun JsonPrimitive.scalar(): Any =
    if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
