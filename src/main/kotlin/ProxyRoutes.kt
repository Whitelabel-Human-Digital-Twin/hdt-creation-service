package com.example.com

import com.example.com.query.AggregateQuery
import com.example.com.query.WhdtQuery
import com.example.com.query.WldtQuery
import com.example.com.util.HdtRegistry
import io.github.whdt.distributed.serde.Stub
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Stub.hdtJson)
    }
}

fun Application.configureProxyRoutes() {

    routing {
        get("api/hdt/{id}/state") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/state")
        }

        get("api/hdt/{id}/state/changes") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/state/changes")
        }

        get("api/hdt/{id}/state/previous") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/state/previous")
        }

        get("api/hdt/{id}/state/properties") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/state/properties")
        }

        get("api/hdt/{id}/state/history") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(
                call,
                "$dtUrl/storage/query",
                HttpMethod.Post,
                WldtQuery.getStateWithRange(0, 10).toJsonString())
        }

        get("api/hdt/{id}/properties/{propertyName}") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            val propertyName = call.parameters["propertyName"] ?: return@get call.respondText(
                "Missing property name", status = HttpStatusCode.BadRequest
            )
            proxy(call, "$dtUrl/properties/$propertyName")
        }

        get("api/hdt/{id}/storage") {
            val dtId = call.parameters["id"] ?: return@get call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@get call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/storage")
        }

        // TODO("make proxy calls for queries")
        post("api/hdt/{id}/storage/query") {
            val dtId = call.parameters["id"] ?: return@post call.respondText(
                "Missing Digital Twin ID", status = HttpStatusCode.BadRequest
            )

            val dtUrl = HdtRegistry.getUrl(dtId) ?: return@post call.respondText(
                "Digital Twin not found", status = HttpStatusCode.NotFound
            )
            proxy(call, "$dtUrl/storage", HttpMethod.Post)
        }

        post("api/query/property/timefilter") {
            val query = call.receive<WhdtQuery>()
            when (query) {
                is AggregateQuery -> {

                }
            }
        }
    }
}

suspend fun proxy(
    call: ApplicationCall,
    path: String,
    method: HttpMethod = HttpMethod.Get,
    body: Any? = null,
) {
    val response: HttpResponse = try {
        httpClient.request(path) {
            this.method = method

            // Forward query parameters
            url {
                call.request.queryParameters.forEach { key, values ->
                    values.forEach { value -> parameters.append(key, value) }
                }
            }

            // Forward body for POST/PUT/etc.
            if (method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) {
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                } else {
                    val raw = call.receiveText()
                    setBody(raw)
                    headers {
                        append(HttpHeaders.ContentType, call.request.contentType().toString())
                    }
                }
            }
        }
    } catch (e: Exception) {
        return call.respondText(
            "Failed to reach service at $path: ${e.message}",
            status = HttpStatusCode.BadGateway
        )
    }

    val content = response.bodyAsText()
    call.respondText(content, status = response.status)
}