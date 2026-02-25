package com.example.com

import com.example.com.util.HdtUtils
import com.example.com.util.HdtRegistry
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.csv.parser.ParserCSV
import io.github.whdt.distributed.serde.Stub
import io.github.whdt.wldt.plugin.execution.WldtApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val app = WldtApp()
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Stub.hdtJson)
        }
    }

    routing {
        get("api/hdt") {
            val dts = HdtRegistry.getRegisteredIds()
            println("Requested dts: $dts")
            call.respond(dts)
        }

        post("api/hdt/new") {
            try {
                val hdt = call.receive<HumanDigitalTwin>()
                val newDt = HdtUtils.setupHdt(hdt)
                app.addStart(newDt)
                call.respond(HttpStatusCode.Created, newDt)
            } catch (e: Exception) {
                println("Deserialization failed: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, "Invalid HumanDigitalTwin JSON: ${e.message}")
            }
        }

        post("api/hdt/csv") {
            val contentType = call.request.contentType()

            if (contentType != ContentType.Text.CSV) {
                return@post call.respondText(
                    "Invalid content type. Expected text/csv",
                    status = HttpStatusCode.UnsupportedMediaType
                )
            }

            val csvBody = call.receiveText()
            if (csvBody.isBlank()) {
                return@post call.respondText(
                    "CSV body is empty",
                    status = HttpStatusCode.BadRequest
                )
            }

            val hdtMap = ParserCSV.createParserCSV().parsing(csvBody)

            hdtMap
                .mapNotNull { HdtUtils.hdtFrom(it.key, it.value.toList()) }
                .map {
                    val newDt = HdtUtils.setupHdt(it)
                    app.addStart(newDt)
                    newDt
                }.forEach {
                    try {
                        val response = client.post("http://localhost:8081/api/hdts") {
                            contentType(ContentType.Application.Json)
                            setBody(it)
                        }

                        println("Response status: ${response.status}")
                        println("Response body: ${response.bodyAsText()}")

                    } catch (e: Exception) {
                        println("EXCEPTION CALLING DB SERVICE:")
                        e.printStackTrace()
                    }
                }

            // For now, just respond with success
            call.respondText("CSV received successfully", status = HttpStatusCode.OK)
        }
    }
}
