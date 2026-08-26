package wang.harlon.eventbase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal class HttpSink(private val client: HttpClient) : Sink {

    override suspend fun send(batch: Batch): SendResult {
        val response: HttpResponse = client.post("${batch.config.endpoint.trimEnd('/')}/e") {
            // 消费方若全局开了 expectSuccess，4xx 会先抛异常 → 被判 RETRY → 无效事件永久卡队列
            expectSuccess = false
            header("App-Key", batch.config.appKey)
            contentType(ContentType.Application.Json)
            setBody(body(batch))
        }
        if (batch.config.logEvents) {
            logLine("POST /e -> ${response.status.value} (${batch.events.size} events)")
        }
        // 4xx 与 204 一律出队：服务端已判定，重试无意义。只有 5xx 与网络错误才留。
        return if (response.status.value < 500) SendResult.DROP else SendResult.RETRY
    }
}

internal fun body(batch: Batch): String =
    buildJsonObject {
        put("install", JsonPrimitive(batch.install))
        put("session", JsonPrimitive(batch.session))
        batch.user?.let { put("user", JsonPrimitive(it)) }
        batch.config.deviceId?.let { put("device", JsonPrimitive(it)) }
        put(
            "sys",
            buildJsonObject {
                put("version", JsonPrimitive(batch.config.appVersion))
                put("platform", JsonPrimitive(batch.config.platform))
                put("channel", JsonPrimitive(batch.config.channel))
                put("locale", JsonPrimitive(batch.config.locale))
                put("debug", JsonPrimitive(batch.config.isDebug))
            },
        )
        put(
            "events",
            buildJsonArray {
                batch.events.forEach { event ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(event.id))
                            put("name", JsonPrimitive(event.name))
                            put("at", JsonPrimitive(event.at))
                            event.flow?.let { put("flow", JsonPrimitive(it)) }
                            put("props", encodeProps(event.props))
                        }
                    )
                }
            },
        )
    }.toString()
