package wang.harlon.eventbase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class FakeClock(var current: Long = 1_700_000_000_000) : Clock {
    override fun now(): Long = current
    fun advance(ms: Long) {
        current += ms
    }
}

class FailingSink(var result: SendResult = SendResult.RETRY) : Sink {
    var attempts = 0
    override suspend fun send(batch: Batch): SendResult {
        attempts++
        return result
    }
}

class ThrowingSink : Sink {
    var attempts = 0
    override suspend fun send(batch: Batch): SendResult {
        attempts++
        throw IllegalStateException("network down")
    }
}

data class TestEvent(
    override val name: String,
    override val props: Map<String, Any?> = emptyMap(),
) : Event

fun testConfig(flushAt: Int = 1000) = EventbaseConfig(
    endpoint = "https://example.invalid/t",
    appKey = "app-key",
    appVersion = "1.3.0",
    platform = "android",
    channel = "play",
    locale = "zh-Hans-CN",
    flushAt = flushAt,
)

fun client(
    sink: Sink,
    storage: Storage = MemoryStorage(),
    clock: Clock = FakeClock(),
    config: EventbaseConfig = testConfig(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) = EventbaseClient(config, storage, sink, clock, scope)
