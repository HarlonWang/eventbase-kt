package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PropertiesTest {

    private enum class Source { INSTALLED }

    @Test
    fun aPropertyRidesOnEveryLaterEvent() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.track(TestEvent("before"))
        c.setProperty("bundle_version", "v1")
        c.track(TestEvent("content_opened", mapOf("rank" to 3L)))
        c.track(TestEvent("app_backgrounded"))
        c.flush()

        assertEquals(emptyMap(), sink.propsOf("before"), "not attached retroactively")
        assertEquals(mapOf("rank" to 3L, "bundle_version" to "v1"), sink.propsOf("content_opened"))
        assertEquals(mapOf("bundle_version" to "v1"), sink.propsOf("app_backgrounded"))
    }

    @Test
    fun anEventsOwnPropertyWins() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.setProperty("source", "global")
        c.track(TestEvent("content_opened", mapOf("source" to "github")))
        c.flush()

        assertEquals(mapOf("source" to "github"), sink.propsOf("content_opened"))
    }

    @Test
    fun valuesAreSnapshotAtTrackTimeAndNullRemoves() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.setProperty("v", "1")
        c.track(TestEvent("first"))
        c.setProperty("v", Source.INSTALLED)
        c.track(TestEvent("second"))
        c.setProperty("v", null)
        c.track(TestEvent("third"))
        c.setProperty("w", 2)
        c.removeProperty("w")
        c.track(TestEvent("fourth"))
        c.flush()

        assertEquals(mapOf("v" to "1"), sink.propsOf("first"))
        assertEquals(mapOf("v" to "installed"), sink.propsOf("second"), "canonicalized like event props")
        assertEquals(emptyMap(), sink.propsOf("third"))
        assertEquals(emptyMap(), sink.propsOf("fourth"))
    }

    @Test
    fun anEventThatWouldExceedTheKeyLimitCarriesNoGlobalProperties() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.setProperty("a", "1")
        c.setProperty("b", "2")
        val nineteen = (1..19).associate { "k$it" to "x" }
        val twenty = (1..20).associate { "k$it" to "x" }
        c.track(TestEvent("roomy", (1..18).associate { "k$it" to "x" }))
        c.track(TestEvent("tight", nineteen))
        c.track(TestEvent("full", twenty))
        c.flush()

        assertEquals(20, sink.propsOf("roomy").size)
        assertEquals(nineteen, sink.propsOf("tight"), "all or nothing: half the global set would be worse than none")
        assertEquals(twenty, sink.propsOf("full"))
    }

    @Test
    fun aKeyTheServerWouldRejectIsNeverSet() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.setProperty("", "x")
        c.setProperty("k".repeat(41), "x")
        c.setProperty("k".repeat(40), "ok")
        c.track(TestEvent("e"))
        c.flush()

        assertEquals(mapOf("k".repeat(40) to "ok"), sink.propsOf("e"))
    }

    @Test
    fun propertiesDoNotOutliveTheProcess() = runTest {
        val storage = MemoryStorage()
        client(RecordingSink(), storage).setProperty("v", "1")
        val sink = RecordingSink()
        val next = client(sink, storage)
        next.track(TestEvent("e"))
        next.flush()

        assertEquals(emptyMap(), sink.propsOf("e"))
    }
}
