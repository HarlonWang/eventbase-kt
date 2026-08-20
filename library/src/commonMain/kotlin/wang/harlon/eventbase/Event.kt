package wang.harlon.eventbase

/**
 * 事件词汇由 App 定义，库不认识具体事件。
 * 命名与属性规范见服务端仓 docs/telemetry-design.md 的事件词汇表。
 */
interface Event {
    val name: String
    val props: Map<String, Any?>
        get() = emptyMap()
}

/**
 * [session] 与 [user] 在**入队时刻**定格，不在 flush 时取当前值——否则积压事件会被
 * 打上重启后的新 session，匿名期事件会被算到之后登录的账号头上。
 */
data class QueuedEvent(
    /** 幂等 id，入队时生成。服务端当前只存不去重，用来量化重复率——见服务端仓 docs/protocol.md */
    val id: String,
    val name: String,
    val at: Long,
    val flow: String?,
    val props: Map<String, Any?>,
    val session: String,
    val user: String?,
)
