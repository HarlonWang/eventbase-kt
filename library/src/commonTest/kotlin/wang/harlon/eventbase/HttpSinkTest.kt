package wang.harlon.eventbase

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class HttpSinkTest {

    private fun sink(status: HttpStatusCode, expectSuccess: Boolean): HttpSink {
        val engine = MockEngine { respond("", status) }
        return HttpSink(HttpClient(engine) { this.expectSuccess = expectSuccess })
    }

    private val batch = Batch("install-1", "session-1", null, testConfig(), listOf(
        QueuedEvent("event-1", "app_opened", 1_700_000_000_000, null, emptyMap(), "session-1", null)
    ))

    @Test
    fun acceptedBatchIsDropped() = runTest {
        assertEquals(SendResult.DROP, sink(HttpStatusCode.NoContent, false).send(batch))
    }

    @Test
    fun serverErrorIsRetried() = runTest {
        assertEquals(SendResult.RETRY, sink(HttpStatusCode.InternalServerError, false).send(batch))
    }

    /** 消费方全局开了 expectSuccess 时，4xx 不能被抛成异常再被判成重试。 */
    @Test
    fun rejectedBatchIsDroppedEvenWhenClientExpectsSuccess() = runTest {
        assertEquals(SendResult.DROP, sink(HttpStatusCode.BadRequest, true).send(batch))
    }
}
