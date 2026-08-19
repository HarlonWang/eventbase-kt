package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class ResilienceTest {

    @Test
    fun concurrentTracksDoNotCorruptTheQueue() = runTest {
        val c = client(FailingSink(), config = testConfig())

        withContext(Dispatchers.Default) {
            repeat(200) { i -> launch { c.track(TestEvent("e$i")) } }
        }

        assertEquals(200, c.queued().size)
    }

    @Test
    fun oneCorruptedEntryDoesNotWipeTheQueue() = runTest {
        val storage = MemoryStorage()
        val good = client(FailingSink(), storage)
        good.track(TestEvent("kept_one"))
        good.track(TestEvent("kept_two"))

        val raw = storage.get("eventbase.queue")!!
        storage.put("eventbase.queue", raw.replaceFirst("{\"name\":\"kept_one\"", "{\"nome\":\"kept_one\""))

        val sink = RecordingSink()
        client(sink, storage).flush()

        assertEquals(listOf("kept_two"), sink.names)
    }

    @Test
    fun expiredEventsAreDroppedEvenWhenTheClockMovesBackward() = runTest {
        val clock = FakeClock()
        val storage = MemoryStorage()
        val stale = client(FailingSink(), storage, clock)
        stale.track(TestEvent("stale"))

        clock.advance(Limits.MAX_AGE_MS + 1)
        val sink = RecordingSink()
        val c = client(sink, storage, clock)
        c.track(TestEvent("fresh"))
        clock.current -= Limits.MAX_AGE_MS + 1
        clock.advance(Limits.MAX_AGE_MS + 1)
        c.flush()

        assertTrue("stale" !in sink.names)
        assertEquals(listOf("fresh"), sink.names)
    }

    @Test
    fun repeatedInitReturnsTheSameClient() {
        Eventbase.reset()
        try {
            val storage = MemoryStorage()
            val first = Eventbase.initForTest(RecordingSink(), storage = storage)
            val second = Eventbase.initForTest(RecordingSink(), storage = storage)

            assertSame(first, second)
        } finally {
            Eventbase.reset()
        }
    }

    @Test
    fun concurrentInitInstallsExactlyOneClient() = runTest {
        Eventbase.reset()
        try {
            val storage = MemoryStorage()
            val installed = mutableListOf<EventbaseClient>()
            withContext(Dispatchers.Default) {
                repeat(50) {
                    launch { installed += Eventbase.initForTest(RecordingSink(), storage = storage) }
                }
            }

            assertEquals(1, installed.distinct().size)
        } finally {
            Eventbase.reset()
        }
    }

    @Test
    fun mutableNestedPropertyValuesAreSnapshotted() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        val tags = mutableListOf("a")

        c.track(TestEvent("content_opened", mapOf("tags" to tags)))
        tags += "b"
        c.flush()

        assertEquals("[a]", sink.propsOf("content_opened")["tags"])
    }
}
