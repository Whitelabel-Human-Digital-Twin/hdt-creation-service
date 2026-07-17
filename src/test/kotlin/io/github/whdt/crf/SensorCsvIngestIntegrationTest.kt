package io.github.whdt.crf

import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the full `POST api/sensors/csv` flow against a stub persistence service,
 * verifying the HDT is upserted once and the observations are streamed in chunks.
 */
class SensorCsvIngestIntegrationTest {

    @Test
    fun `happy path upserts one hdt and streams chunked observations`() = testApplication {
        val hdtsReceived = AtomicInteger(0)
        val obsBatches = AtomicInteger(0)
        val obsReceived = AtomicInteger(0)

        // Stub persistence service on a random port.
        val persistence = embeddedServer(Netty, port = 0) {
            routing {
                put("/hdts/batch") {
                    val n = Json.parseToJsonElement(call.receiveText()).jsonArray.size
                    hdtsReceived.addAndGet(n)
                    call.respond(HttpStatusCode.OK)
                }
                post("/observations/batch") {
                    val n = Json.parseToJsonElement(call.receiveText()).jsonArray.size
                    obsBatches.incrementAndGet()
                    obsReceived.addAndGet(n)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }.start(wait = false)

        try {
            val port = persistence.engine.resolvedConnectors().first().port

            environment {
                config = MapApplicationConfig("app.persistenceServiceUrl" to "http://localhost:$port")
            }
            application {
                install(ContentNegotiation) { json(Stub.hdtJson) }
                configureRouting()
            }

            // 3000 frames × 2 columns = 6000 observations => 2 chunks at 5000/chunk.
            val frameCount = 3000
            val csv = buildString {
                appendLine("sens1_x,sens1_y")
                repeat(frameCount) { f ->
                    appendLine("${f.toDouble()},${f + 0.5}")
                }
            }

            val response = client.post("/api/sensors/csv") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                csv,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "text/csv")
                                    append(HttpHeaders.ContentDisposition, "filename=\"01A101_nw_acc.csv\"")
                                },
                            )
                        }
                    )
                )
            }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(1, hdtsReceived.get(), "exactly one HDT should be upserted")
            assertEquals(frameCount * 2, obsReceived.get(), "all observations should reach persistence")
            assertTrue(obsBatches.get() >= 2, "observations should be streamed in multiple chunks")
        } finally {
            persistence.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        }
    }
}
