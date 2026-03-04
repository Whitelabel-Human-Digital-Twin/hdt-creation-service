package com.example.com

import com.example.com.mimosa.Mapper
import com.example.com.mimosa.ParserMimosa.parseMimosaWorkbook
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.distributed.serde.Stub
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

fun Application.configureRouting() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Stub.hdtJson)
        }
    }

    routing {

        post("api/v2/hdt") {
            val mp = call.receiveMultipart()
            val tempPath = mp.readPartAsTempFile(
                fieldName = "file",
                maxBytes = 30L * 1024 * 1024
            ) ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing field 'file'")
            try {
                val wr = withContext(Dispatchers.IO) { parseMimosaWorkbook(tempPath) }
                Mapper.workbookResultToHDTs(wr, makeHdtId = HdtId::of)
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
            } finally {
                tempPath.deleteIfExists()
            }
        }
    }
}

suspend fun MultiPartData.readPartAsTempFile(
    fieldName: String,
    maxBytes: Long
): Path? {
    var tempFile: Path? = null
    forEachPart { part ->
        try {
            if (part is PartData.FileItem && part.name == fieldName) {
                val fileName = part.originalFileName ?: "upload.xlsx"
                tempFile = Files.createTempFile("upload-", "-$fileName")
                var written = 0L
                Files.newOutputStream(tempFile!!).use { output ->
                    part.streamProvider().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break

                            written += read
                            if (written > maxBytes) {
                                throw IllegalArgumentException("File too large")
                            }

                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        } finally {
            part.dispose()
        }
    }
    return tempFile
}