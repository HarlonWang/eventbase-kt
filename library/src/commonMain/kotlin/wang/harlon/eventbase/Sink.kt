package wang.harlon.eventbase

data class Batch(
    val install: String,
    val session: String,
    val user: String?,
    val config: EventbaseConfig,
    val events: List<QueuedEvent>,
)

/** 上报结果决定队列去留：DROP 出队，RETRY 留在队列等下次。 */
enum class SendResult { DROP, RETRY }

interface Sink {
    suspend fun send(batch: Batch): SendResult
}
