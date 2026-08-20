package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

/** session 与 user 必须在入队时刻定格，见 QueuedEvent 的注释。 */
class AttributionTest {

    @Test
    fun anonymousEventsStayAnonymousAfterLogin() = runTest {
        val sink = RecordingSink()
        val c = client(sink)

        c.track(TestEvent("app_opened"))
        c.setUserId("identity-1")
        c.track(TestEvent("content_opened"))
        c.flush()

        assertEquals(null, sink.userOf("app_opened"))
        assertEquals("identity-1", sink.userOf("content_opened"))
    }

    @Test
    fun eventsQueuedBeforeRestartKeepTheOldSession() = runTest {
        val storage = MemoryStorage()
        val first = client(FailingSink(), storage)
        first.track(TestEvent("app_opened"))

        val sink = RecordingSink()
        val second = client(sink, storage)
        second.track(TestEvent("app_backgrounded"))
        second.flush()

        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(first.sessionId, sink.sessionOf("app_opened"))
        assertEquals(second.sessionId, sink.sessionOf("app_backgrounded"))
    }

    @Test
    fun oneBatchNeverMixesSessionsOrUsers() = runTest {
        val storage = MemoryStorage()
        client(FailingSink(), storage).track(TestEvent("from_old_session"))

        val sink = RecordingSink()
        val c = client(sink, storage)
        c.track(TestEvent("from_new_session"))
        c.flush()

        assertEquals(listOf(listOf("from_old_session"), listOf("from_new_session")), sink.batches())
    }

    @Test
    fun everyEventGetsItsOwnId() = runTest {
        val c = client(RecordingSink())
        repeat(3) { c.track(TestEvent("app_opened")) }

        assertEquals(3, c.queued().map { it.id }.distinct().size)
    }

    /** 重传必须复用同一个 id，否则服务端永远没法把重复项认出来 */
    @Test
    fun idSurvivesRestartAndRetry() = runTest {
        val storage = MemoryStorage()
        val first = client(FailingSink(), storage)
        first.track(TestEvent("app_opened"))
        val originalId = first.queued().single().id

        val sink = RecordingSink()
        client(sink, storage).flush()

        assertEquals(originalId, sink.idOf("app_opened"))
    }

    @Test
    fun callerCannotMutatePropsAfterTracking() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        val props = mutableMapOf<String, Any?>("rank" to 1)

        c.track(TestEvent("content_opened", props))
        props["rank"] = 999
        c.flush()

        assertEquals(1L, sink.propsOf("content_opened")["rank"])
    }
}
