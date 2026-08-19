package wang.harlon.eventbase

/** 测试替身：消费方在自己的单测里断言事件与属性，不发网络。 */
class RecordingSink : Sink {
    private val batches = mutableListOf<Batch>()

    val names: List<String> get() = batches.flatMap { batch -> batch.events.map { it.name } }

    fun propsOf(name: String): Map<String, Any?> =
        batches.flatMap { it.events }.first { it.name == name }.props

    fun flowOf(name: String): String? =
        batches.flatMap { it.events }.first { it.name == name }.flow

    fun userOf(name: String): String? =
        batches.first { batch -> batch.events.any { it.name == name } }.user

    fun sysOf(name: String): EventbaseConfig =
        batches.first { batch -> batch.events.any { it.name == name } }.config

    fun clear() = batches.clear()

    override suspend fun send(batch: Batch): SendResult {
        batches.add(batch)
        return SendResult.DROP
    }
}
