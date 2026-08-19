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
private const val KEY_PENDING = "eventbase.queue.pending"

/** 攒够这么多条就把 pending 合进 base；决定了单次 track 的落盘规模上限 */
private const val COMPACT_AT = Limits.BATCH

/**
 * 落盘队列。溢出丢最老、出队前丢弃过期事件——**自清窗口必须与服务端的拒收窗口同一个数**，
 * 否则客户端死攥老事件攒到最后也是被拒，白占队列白耗电。
 *
 * 所有状态变更走 [lock]：track 在调用方线程入队，flush 在后台协程出队。
 */
internal class EventQueue(private val storage: Storage, private val clock: Clock) {
    private val lock = createLock()
    private val items = ArrayDeque<QueuedEvent>()

    /** 自上次合并以来新入队的事件，单独落一个小 key——见 [persistAppend] */
    private val pending = mutableListOf<QueuedEvent>()

    init {
        storage.get(KEY)?.let { items.addAll(decode(it)) }
        val hadPending = storage.get(KEY_PENDING)?.also { items.addAll(decode(it)) } != null
        // KEY_PENDING 是覆盖写而非追加。启动时若不立刻合并，重启后第一次 add() 会把
        // 磁盘上这批事件抹成只剩新入队的那条，它们就只活在内存里了。
        if (hadPending) persist()
    }

    val size: Int get() = lock.withLock { items.size }

    fun add(event: QueuedEvent) {
        lock.withLock {
            items.addLast(event)
            val overflowed = items.size > Limits.QUEUE_CAP
            while (items.size > Limits.QUEUE_CAP) items.removeFirst()
            pending += event
            // 队满丢了队头，或 pending 攒够了，才重写整个 base；其余情况只追加一个小 key。
            // 每次 track 都全量序列化 500 条的话，开销会落在调用方线程上。
            if (overflowed || pending.size >= COMPACT_AT) persist() else persistAppend()
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

    /**
     * 合并：base 写全量，pending 清空。两次写不是原子的——中间被杀会让下次启动同时读到
     * base 与旧 pending，**最多重复上报 24 条**。这个方向是有意的（宁重勿丢）；真要根治
     * 需要事件带幂等 id 由服务端去重，属协议变更，记在 docs/review-findings.md。
     */
    private fun persist() {
        if (items.isEmpty()) storage.remove(KEY) else storage.put(KEY, encode(items))
        pending.clear()
        storage.remove(KEY_PENDING)
    }

    private fun persistAppend() {
        storage.put(KEY_PENDING, encode(pending))
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

/**
 * 入队即把属性摊平成标量。两个作用：调用方之后改自己的 map 或其中的可变值都影响不到
 * 已入队的事件；内存里的值与落盘往返回来的值同型（整数一律 Long、小数一律 Double），
 * 自定义 Sink 不会在重启前后看到不同类型。
 */
internal fun canonicalProps(props: Map<String, Any?>): Map<String, Any> = buildMap {
    props.forEach { (key, value) ->
        when (value) {
            null -> Unit
            is String -> put(key, value)
            is Boolean -> put(key, value)
            is Byte, is Short, is Int, is Long -> put(key, (value as Number).toLong())
            is Float, is Double -> put(key, (value as Number).toDouble())
            is Enum<*> -> put(key, value.name.lowercase())
            else -> put(key, value.toString())
        }
    }
}

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

/**
 * 逐条解析：一条坏数据只丢它自己，不能带走整个队列。
 * 缺字段的记录直接丢——本库尚未发版、无存量队列；**发版后再改字段必须带迁移**。
 */
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
