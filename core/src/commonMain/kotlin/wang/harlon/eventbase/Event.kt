package wang.harlon.eventbase

/**
 * 事件词汇由 App 定义，库不认识具体事件——词汇是 App 特有的。
 * 命名与属性规范见服务端仓 docs/telemetry-design.md 的事件词汇表。
 */
interface Event {
    val name: String
    val props: Map<String, Any?>
        get() = emptyMap()
}

data class QueuedEvent(
    val name: String,
    val at: Long,
    val flow: String?,
    val props: Map<String, Any?>,
)
