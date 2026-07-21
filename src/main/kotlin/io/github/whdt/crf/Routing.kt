package io.github.whdt.crf

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.distributed.serde.Stub
import io.github.whdt.crf.importer.CrfImportConfig
import io.github.whdt.crf.importer.CrfImportService
import io.github.whdt.crf.csv.CsvSensorAssembler
import io.github.whdt.crf.csv.CsvSensorReader
import io.github.whdt.crf.csv.SensorCsvNaming
import io.github.whdt.crf.importer.util.ImportLoggingUtils
import io.github.whdt.crf.importer.util.readPartAsTempFile
import io.github.whdt.crf.importer.util.readSensorMultipart
import io.github.whdt.crf.json.BatchImportOutcome
import io.github.whdt.crf.json.JsonArrayImporter
import io.github.whdt.crf.json.JsonDomainAssembler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

/** Max observations sent per `/observations/batch` call; a full sensor file is far larger. */
private const val OBSERVATION_CHUNK_SIZE = 5000

fun Application.configureRouting() {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Stub.hdtJson)
        }
    }

    val persistenceServiceUrl: String =
        environment.config
            .propertyOrNull("app.persistenceServiceUrl")
            ?.getString()
            ?.takeIf { it.isNotBlank() }
            ?: "http://localhost:8081"

    routing {
        get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "ok")) }

        post("api/hdts/multipart") {
            val mp = call.receiveMultipart()
            val tempFile = mp.readPartAsTempFile(
                fieldName = "file",
                maxBytes = 30L * 1024 * 1024
            ) ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing field 'file'")

            try {
                val service = CrfImportService(
                    config = CrfImportConfig(
                        excludedSheetNames = setOf("sigle", "legend", "legenda"),
                        visitDateAliases = setOf("data_dim", "data_follow_up")
                    ),
                )
                val result = tempFile.toFile().inputStream().use { input ->
                    service.import(input)
                }

                withContext(Dispatchers.IO) {
                    try {
                        val logDir = ImportLoggingUtils.createLogDir()
                        val ts = ImportLoggingUtils.timestamp()

                        val jsonFile = logDir.resolve("output_$ts.json")
                        val obsFile = logDir.resolve("observations_$ts.json")
                        val reportFile = logDir.resolve("report_$ts.txt")

                        val json = Json {
                            prettyPrint = true
                            encodeDefaults = true
                        }
                        Files.writeString(
                            jsonFile,
                            json.encodeToString(result.hdts)
                        )
                        Files.writeString(
                            obsFile,
                            json.encodeToString(result.observations)
                        )

                        // Human readable report
                        Files.writeString(
                            reportFile,
                            ImportLoggingUtils.reportToText(result.report)
                        )

                    } catch (e: Exception) {
                        // Do NOT fail request for logging issues
                        println("Logging failed: ${e.message}")
                    }
                }

                val hdtResponse = client.put("$persistenceServiceUrl/hdts/batch") {
                    contentType(ContentType.Application.Json)
                    setBody(result.hdts)
                }

                if (!hdtResponse.status.isSuccess()) {
                    return@post respondWithError("Hdt Batch Upsert failed", call, hdtResponse)
                }

                val obsResponse = client.post("$persistenceServiceUrl/observations/batch") {
                    contentType(ContentType.Application.Json)
                    setBody(result.observations)
                }
                if (!obsResponse.status.isSuccess()) {
                    return@post respondWithError("Hdt Batch Upsert failed", call, obsResponse)
                }

                call.respondText("CSV received successfully", status = HttpStatusCode.OK)
            } finally {
                tempFile.deleteIfExists()
            }
        }

        post("api/hdts/sensor/multipart") {
            val mp = call.receiveMultipart()
            val upload = mp.readSensorMultipart(maxBytes = 64L * 1024 * 1024)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing field 'file'")

            try {
                val identifiers = try {
                    SensorCsvNaming.resolve(
                        fileName = upload.originalFileName,
                        patientIdOverride = upload.patientId,
                        taskOverride = upload.task,
                        sensorOverride = upload.sensor,
                    )
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid identifiers")
                }

                val parsed = try {
                    upload.file.toFile().inputStream().use { input ->
                        CsvSensorReader().read(input)
                    }
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid CSV: ${e.message}")
                }

                val result = try {
                    CsvSensorAssembler().assemble(identifiers, parsed)
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid sensor data: ${e.message}")
                }

                // Upsert the (shell) HDT and its sensor model first.
                val hdtResponse = client.put("$persistenceServiceUrl/hdts/batch") {
                    contentType(ContentType.Application.Json)
                    setBody(listOf(result.hdt))
                }
                if (!hdtResponse.status.isSuccess()) {
                    return@post respondWithError("Hdt Batch Upsert failed", call, hdtResponse)
                }

                // A full file is ~30k observations, too many for one request; stream in chunks.
                for (chunk in result.observations.chunked(OBSERVATION_CHUNK_SIZE)) {
                    val obsResponse = client.post("$persistenceServiceUrl/observations/batch") {
                        contentType(ContentType.Application.Json)
                        setBody(chunk)
                    }
                    if (!obsResponse.status.isSuccess()) {
                        return@post respondWithError("Observation Batch insert failed", call, obsResponse)
                    }
                }

                call.respondText(
                    "Ingested sensor '${identifiers.sensor}' for patient '${identifiers.patientId}': " +
                        "${parsed.frames.size} frames, ${result.observations.size} observations",
                    status = HttpStatusCode.Created,
                )
            } finally {
                upload.file.deleteIfExists()
            }
        }

        post("api/hdts/json") {
            val json = try {
                call.receive<JsonObject>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body: ${e.message}")
            }

            val assembler = JsonDomainAssembler()
            val result = try {
                assembler.assemble(json)
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid input")
            }

            val hdt = HumanDigitalTwin(
                hdtId = HdtId(result.hdtId),
                models = result.models,
            )

            val hdtResponse = client.put("$persistenceServiceUrl/hdts/batch") {
                contentType(ContentType.Application.Json)
                setBody(listOf(hdt))
            }
            if (!hdtResponse.status.isSuccess()) {
                return@post respondWithError("Hdt Batch Upsert failed", call, hdtResponse)
            }

            val obsResponse = client.post("$persistenceServiceUrl/observations/batch") {
                contentType(ContentType.Application.Json)
                setBody(result.observations)
            }
            if (!obsResponse.status.isSuccess()) {
                return@post respondWithError("Observation Batch insert failed", call, hdtResponse)
            }

            call.respond(HttpStatusCode.Created, result.hdtId)
        }

        post("api/hdts/json/batch") {
            val array = try {
                call.receive<JsonArray>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON array body: ${e.message}")
            }

            when (val outcome = JsonArrayImporter().import(array)) {
                is BatchImportOutcome.EmptyInput ->
                    call.respond(HttpStatusCode.BadRequest, "Empty array: nothing to import")

                is BatchImportOutcome.ElementFailure ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Invalid element at index ${outcome.index}: ${outcome.reason}",
                    )

                is BatchImportOutcome.Success -> {
                    val hdtResponse = client.put("$persistenceServiceUrl/hdts/batch") {
                        contentType(ContentType.Application.Json)
                        setBody(outcome.hdts)
                    }
                    if (!hdtResponse.status.isSuccess())
                        return@post call.respond(HttpStatusCode.InternalServerError, "HDT batch upsert failed")

                    val obsResponse = client.post("$persistenceServiceUrl/observations/batch") {
                        contentType(ContentType.Application.Json)
                        setBody(outcome.observations)
                    }
                    if (!obsResponse.status.isSuccess())
                        return@post call.respond(HttpStatusCode.InternalServerError, "Observation batch insert failed")

                    call.respond(HttpStatusCode.Created, outcome.hdtIds)
                }
            }
        }
    }
}

suspend fun respondWithError(failurePrologue: String, call: RoutingCall, response: HttpResponse) {
    val detail = response.bodyAsText()
    call.application.log.error(failurePrologue + ": ${response.status} — $detail")
    return call.respond(HttpStatusCode.InternalServerError, failurePrologue + ": ${response.status} — $detail")
}