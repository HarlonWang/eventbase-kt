package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LifecycleTest {

    private fun tracker(client: EventbaseClient, clock: Clock, onFlush: () -> Unit = {}) =
        LifecycleTracker(client, clock, onFlush)

    @Test
    fun firstForegroundReportsAppOpenedOnce() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        val t = tracker(c, FakeClock())

        t.onForeground()
        t.onBackground()
        t.onForeground()
        c.flush()

        assertEquals(1, sink.names.count { it == "app_opened" })
    }

    @Test
    fun backgroundReportsDurationInSeconds() = runTest {
        val sink = RecordingSink()
        val clock = FakeClock()
        val c = client(sink)
        val t = tracker(c, clock)

        t.onForeground()
        clock.advance(90_000)
        t.onBackground()
        c.flush()

        assertEquals(90L, sink.propsOf("app_backgrounded")["duration_s"])
    }

    @Test
    fun subSecondSessionIsNotReported() = runTest {
        val sink = RecordingSink()
        val clock = FakeClock()
        val c = client(sink)
        val t = tracker(c, clock)

        t.onForeground()
        clock.advance(300)
        t.onBackground()
        c.flush()

        assertEquals(listOf("app_opened"), sink.names)
    }

    @Test
    fun backgroundWithoutForegroundIsIgnored() = runTest {
        val sink = RecordingSink()
        val c = client(sink)

        tracker(c, FakeClock()).onBackground()
        c.flush()

        assertEquals(emptyList(), sink.names)
    }

    @Test
    fun backgroundTriggersFlush() = runTest {
        var flushed = 0
        val clock = FakeClock()
        val t = tracker(client(RecordingSink()), clock) { flushed++ }

        t.onForeground()
        clock.advance(2_000)
        t.onBackground()

        assertEquals(1, flushed)
    }

    @Test
    fun eventsBeforeFirstForegroundMarkTheSessionAsWoken() = runTest {
        val sink = RecordingSink()
        val clock = FakeClock()
        val c = client(sink)
        val t = tracker(c, clock)

        c.track(TestEvent("notification_opened"))
        t.onForeground()
        clock.advance(5_000)
        t.onBackground()
        c.flush()

        assertEquals(true, sink.propsOf("app_backgrounded")["is_wake"])
    }

    @Test
    fun coldStartSessionIsNotMarkedAsWoken() = runTest {
        val sink = RecordingSink()
        val clock = FakeClock()
        val c = client(sink)
        val t = tracker(c, clock)

        t.onForeground()
        clock.advance(5_000)
        t.onBackground()
        c.flush()

        assertEquals(false, sink.propsOf("app_backgrounded")["is_wake"])
    }
}
