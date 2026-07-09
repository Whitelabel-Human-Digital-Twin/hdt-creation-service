package io.github.whdt.crf

import io.github.ktwinx.distributed.serde.Stub
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlin.test.*

class JsonBatchRouteTest {

    private fun withTestApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json(Stub.hdtJson)
            }
            configureRouting()
        }
        block()
    }

    @Test
    fun `returns 400 when body is a JSON object not an array`() = withTestApp {
        val response = client.post("/api/hdts/json/batch") {
            contentType(ContentType.Application.Json)
            setBody("""{"ID":"x","Age":1, "task": "nw"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns 400 for empty array`() = withTestApp {
        val response = client.post("/api/hdts/json/batch") {
            contentType(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns 400 when array contains a non-object element and response includes the index`() = withTestApp {
        val response = client.post("/api/hdts/json/batch") {
            contentType(ContentType.Application.Json)
            setBody("""[{"ID":"x","Age":1,"task":"nw","Sex":"M"},"not-an-object"]""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("1"), "Expected index 1 in response body")
    }

    @Test
    fun `returns 400 when element is missing a required field`() = withTestApp {
        val response = client.post("/api/hdts/json/batch") {
            contentType(ContentType.Application.Json)
            setBody("""[{"Age":30}]""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
