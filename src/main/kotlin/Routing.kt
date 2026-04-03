package com.example.com

import com.example.com.crf.importer.CrfImportConfig
import com.example.com.crf.importer.CrfImportService
import com.example.com.crf.importer.util.readPartAsTempFile
import io.github.whdt.distributed.serde.Stub
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.path.deleteIfExists

fun Application.configureRouting() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Stub.hdtJson)
        }
    }

    routing {
        post("api/hdts/multipart") {
            val mp = call.receiveMultipart()
            val tempFile = mp.readPartAsTempFile(
                fieldName = "file",
                maxBytes = 30L * 1024 * 1024
            ) ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing field 'file'")

            try {
                val service = CrfImportService(
                    config = CrfImportConfig(
                        excludedSheetNames = setOf("sigle", "legend", "legenda")
                    ),
                )
                val result = tempFile.toFile().inputStream().use { input ->
                    service.import(input)
                }
                /*val response = client.put("http://localhost:8081/api/hdts/many") {
                    contentType(ContentType.Application.Json)
                    setBody(result.hdts)
                }

                if (!response.status.isSuccess()) return@post call.respond(HttpStatusCode.InternalServerError, "Unexpected response from server")
*/
                result.hdts.forEach { println(Stub.hdtJsonSerDe().serialize(it)) }
                println(result.report.toString())
                call.respondText("CSV received successfully", status = HttpStatusCode.OK)
            } finally {
                tempFile.deleteIfExists()
            }
        }
    }
}