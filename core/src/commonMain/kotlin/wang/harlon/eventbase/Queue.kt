package wang.harlon.eventbase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val KEY = "eventbase.queue"

/**
 * 落盘队列。溢出丢最老、出队前丢弃过期事件——**自清窗口必须与服务端的拒收窗口同一个数**，
 * 否则客户端死攥老事件攒到最后也是被拒，白占队列白耗电。
 */
internal class EventQueue(private val storage: Storage, private val clock: Clock) {
    private val items = ArrayDeque<QueuedEvent>()

    init {
        storage.get(KEY)?.let { items.addAll(decode(it)) }
    }

    val size: Int get() = items.size

    fun add(event: QueuedEvent) {
        items.addLast(event)
        while (items.size > Limits.QUEUE_CAP) items.removeFirst()
        persist()
    }

    /** 取一批待发事件，同时把过期的清掉。 */
    fun peek(limit: Int = Limits.BATCH): List<QueuedEvent> {
        purgeExpired()
        return items.take(limit)
    }

    fun drop(count: Int) {
        repeat(minOf(count, items.size)) { items.removeFirst() }
        persist()
    }

    fun snapshot(): List<QueuedEvent> = items.toList()

    private fun purgeExpired() {
        val cutoff = clock.now() - Limits.MAX_AGE_MS
        var removed = false
        while (items.isNotEmpty() && items.first().at < cutoff) {
            items.removeFirst()
            removed = true
        }
        if (removed) persist()
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

private fun decode(raw: String): List<QueuedEvent> =
    runCatching {
        (json.parseToJsonElement(raw) as JsonArray).map { element ->
            val obj = element.jsonObject
            QueuedEvent(
                name = obj["name"]!!.jsonPrimitive.content,
                at = obj["at"]!!.jsonPrimitive.long(),
                flow = obj["flow"]?.jsonPrimitive?.content,
                props = obj["props"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.scalar() } ?: emptyMap(),
            )
        }
    }.getOrElse { emptyList() }

private fun JsonPrimitive.long(): Long = longOrNull ?: content.toLong()

private fun JsonPrimitive.scalar(): Any =
    if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
