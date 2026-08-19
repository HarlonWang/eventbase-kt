package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class QueueTest {

    @Test
    fun keepsEventsAcrossRestart() = runTest {
        val storage = MemoryStorage()
        client(FailingSink(), storage).track(TestEvent("app_opened"))

        val sink = RecordingSink()
        client(sink, storage).flush()

        assertEquals(listOf("app_opened"), sink.names)
    }

    @Test
    fun dropsOldestWhenFull() = runTest {
        val storage = MemoryStorage()
        val c = client(FailingSink(), storage, config = testConfig())

        repeat(Limits.QUEUE_CAP + 5) { c.track(TestEvent("e$it")) }

        val queued = c.queued()
        assertEquals(Limits.QUEUE_CAP, queued.size)
        assertEquals("e5", queued.first().name)
        assertEquals("e${Limits.QUEUE_CAP + 4}", queued.last().name)
    }

    @Test
    fun dropsEventsOlderThanServerWindow() = runTest {
        val clock = FakeClock()
        val sink = RecordingSink()
        val c = client(sink, clock = clock)

        c.track(TestEvent("stale"))
        clock.advance(Limits.MAX_AGE_MS + 1)
        c.track(TestEvent("fresh"))
        c.flush()

        assertEquals(listOf("fresh"), sink.names)
    }

    @Test
    fun sendsAtMostOneBatchPerRequest() = runTest {
        val sink = RecordingSink()
        val c = client(sink)

        repeat(Limits.BATCH + 3) { c.track(TestEvent("e$it")) }
        c.flush()

        assertEquals(Limits.BATCH + 3, sink.names.size)
    }

    @Test
    fun retryKeepsEventsQueued() = runTest {
        val sink = FailingSink(SendResult.RETRY)
        val c = client(sink)

        c.track(TestEvent("app_opened"))
        c.flush()

        assertEquals(1, c.queued().size)
        assertEquals(1, sink.attempts)
    }

    @Test
    fun networkFailureIsNotFatal() = runTest {
        val sink = ThrowingSink()
        val c = client(sink)

        c.track(TestEvent("app_opened"))
        c.flush()

        assertEquals(1, c.queued().size)
    }

    @Test
    fun backoffSkipsImmediateRetry() = runTest {
        val clock = FakeClock()
        val sink = FailingSink(SendResult.RETRY)
        val c = client(sink, clock = clock)

        c.track(TestEvent("app_opened"))
        c.flush()
        c.flush()
        assertEquals(1, sink.attempts)

        clock.advance(10_000)
        c.flush()
        assertEquals(2, sink.attempts)
    }

    @Test
    fun successClearsQueue() = runTest {
        val c = client(FailingSink(SendResult.DROP))

        c.track(TestEvent("app_opened"))
        c.flush()

        assertTrue(c.queued().isEmpty())
    }
}
