package com.example.com

import com.example.com.util.HdtUtils
import io.github.whdt.csv.parser.ParserCSV
import io.github.whdt.distributed.serde.Stub
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Stub.hdtJson)
        }
    }

    routing {
        post("api/hdt") {
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
                .forEach {
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
