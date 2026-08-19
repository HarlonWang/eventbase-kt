package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ClientTest {

    @Test
    fun tracksNameAndProps() = runTest {
        val sink = RecordingSink()
        val c = client(sink)

        c.track(TestEvent("content_opened", mapOf("source" to "github", "rank" to 3L)))
        c.flush()

        assertEquals(listOf("content_opened"), sink.names)
        assertEquals(mapOf("source" to "github", "rank" to 3L), sink.propsOf("content_opened"))
    }

    @Test
    fun installIdSurvivesRestartSessionIdDoesNot() {
        val storage = MemoryStorage()
        val first = client(RecordingSink(), storage)
        val second = client(RecordingSink(), storage)

        assertEquals(first.installId, second.installId)
        assertNotEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun seedInstallIdIsAdoptedOnceThenIgnored() {
        val storage = MemoryStorage()
        val seeded = client(RecordingSink(), storage, config = testConfig().copy(installId = "biz-install"))
        assertEquals("biz-install", seeded.installId)

        // 换个种子也不该改已定的 install_id：改了等于把一台设备算成两台
        val later = client(RecordingSink(), storage, config = testConfig().copy(installId = "another"))
        assertEquals("biz-install", later.installId)
    }

    @Test
    fun userIdPersistsAndClears() {
        val storage = MemoryStorage()
        client(RecordingSink(), storage).setUserId("identity-1")
        assertEquals("identity-1", storage.get("eventbase.user"))

        client(RecordingSink(), storage).clearUserId()
        assertNull(storage.get("eventbase.user"))
    }

    @Test
    fun batchCarriesUserAndSys() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        c.setUserId("identity-1")
        c.track(TestEvent("app_opened"))
        c.flush()

        assertEquals("identity-1", sink.userOf("app_opened"))
        assertEquals("1.3.0", sink.sysOf("app_opened").appVersion)
        assertEquals("play", sink.sysOf("app_opened").channel)
    }

    @Test
    fun flowSurvivesProcessRestart() {
        val storage = MemoryStorage()
        val flow = client(RecordingSink(), storage).startFlow()

        assertEquals(flow, client(RecordingSink(), storage).currentFlow())
    }

    @Test
    fun flowRidesOnTheEvent() = runTest {
        val sink = RecordingSink()
        val c = client(sink)
        val flow = c.startFlow()

        c.track(TestEvent("auth_started"), flow)
        c.flush()

        assertEquals(flow, sink.flowOf("auth_started"))
    }

    /** android.util.Log 在宿主单测里未 mock，日志开关不能因此把调用方带崩。 */
    @Test
    fun diagnosticLoggingNeverThrows() = runTest {
        val sink = RecordingSink()
        val c = client(sink, config = testConfig(logEvents = true))

        c.track(TestEvent("app_opened"))
        c.flush()

        assertEquals(listOf("app_opened"), sink.names)
    }

    @Test
    fun flushAtTriggersUpload() = runTest {
        val sink = RecordingSink()
        val c = client(sink, config = testConfig(flushAt = 2))

        c.track(TestEvent("a"))
        assertEquals(0, sink.names.size)

        c.track(TestEvent("b"))
        assertEquals(listOf("a", "b"), sink.names)
    }
}
