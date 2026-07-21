package io.github.whdt.crf

import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensorCsvRouteTest {

    private fun withTestApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json(Stub.hdtJson)
            }
            configureRouting()
        }
        block()
    }

    private fun filePart(
        content: String,
        fileName: String,
    ) = formData {
        append(
            "file",
            content,
            Headers.build {
                append(HttpHeaders.ContentType, "text/csv")
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            },
        )
    }

    @Test
    fun `returns 400 when the file field is missing`() = withTestApp {
        val response = client.post("/api/hdts/sensor/multipart") {
            setBody(
                MultiPartFormDataContent(
                    formData { append("patientId", "01A101") }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("file"))
    }

    @Test
    fun `returns 400 when identifiers cannot be resolved`() = withTestApp {
        // Filename has no parseable tokens and no override fields are supplied.
        val response = client.post("/api/hdts/sensor/multipart") {
            setBody(MultiPartFormDataContent(filePart("a,b\n1.0,2.0", "weird-name.csv")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("patientId"))
    }

    @Test
    fun `returns 400 for a non-numeric CSV body`() = withTestApp {
        val response = client.post("/api/hdts/sensor/multipart") {
            setBody(MultiPartFormDataContent(filePart("a,b\n1.0,oops", "01A101_nw_acc.csv")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Invalid CSV"))
    }

    @Test
    fun `returns 400 when a sensor override contains a colon`() = withTestApp {
        val response = client.post("/api/hdts/sensor/multipart") {
            setBody(
                MultiPartFormDataContent(
                    filePart("a,b\n1.0,2.0", "01A101_nw_acc.csv") + formData {
                        append("sensor", "acc:1")
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Invalid sensor data"))
    }
}
