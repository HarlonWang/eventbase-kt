package wang.harlon.eventbase

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive

class BodyTest {

    @Test
    fun matchesProtocolShape() {
        val batch = Batch(
            install = "install-1",
            session = "session-1",
            user = "identity-1",
            config = testConfig(),
            events = listOf(
                QueuedEvent("event-1", "app_opened", 1_700_000_000_000, "flow-1", mapOf("is_cold" to true), "session-1", "identity-1")
            ),
        )

        val json = Json.parseToJsonElement(body(batch)).jsonObject

        assertEquals("install-1", json["install"]!!.jsonPrimitive.content)
        assertEquals("session-1", json["session"]!!.jsonPrimitive.content)
        assertEquals("identity-1", json["user"]!!.jsonPrimitive.content)

        val sys = json["sys"]!!.jsonObject
        assertEquals("1.3.0", sys["version"]!!.jsonPrimitive.content)
        assertEquals("android", sys["platform"]!!.jsonPrimitive.content)
        assertEquals("play", sys["channel"]!!.jsonPrimitive.content)
        assertEquals("zh-Hans-CN", sys["locale"]!!.jsonPrimitive.content)
        assertEquals(false, sys["debug"]!!.jsonPrimitive.boolean)

        val event = json["events"]!!.jsonArray.single().jsonObject
        assertEquals("event-1", event["id"]!!.jsonPrimitive.content)
        assertEquals("app_opened", event["name"]!!.jsonPrimitive.content)
        assertEquals(1_700_000_000_000, event["at"]!!.jsonPrimitive.long)
        assertEquals("flow-1", event["flow"]!!.jsonPrimitive.content)
        assertContains(event["props"]!!.jsonObject.keys, "is_cold")
    }

    @Test
    fun anonymousBatchOmitsUser() {
        val batch = Batch("install-1", "session-1", null, testConfig(), emptyList())
        assertEquals(null, Json.parseToJsonElement(body(batch)).jsonObject["user"])
    }
}
