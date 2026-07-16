package io.github.whdt.crf.importer.util

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * The parts of a sensor-CSV upload: the CSV itself (streamed to a temp file),
 * its original filename, and the optional `patientId` / `task` / `sensor`
 * override form fields.
 */
data class SensorMultipart(
    val file: Path,
    val originalFileName: String?,
    val patientId: String?,
    val task: String?,
    val sensor: String?,
)

/**
 * Reads a multipart body carrying a sensor CSV in the `file` field plus optional
 * `patientId`, `task` and `sensor` text fields.
 *
 * Returns `null` if no `file` part is present. Throws [IllegalArgumentException]
 * if the file exceeds [maxBytes].
 */
suspend fun MultiPartData.readSensorMultipart(maxBytes: Long): SensorMultipart? {
    var tempFile: Path? = null
    var originalFileName: String? = null
    val fields = mutableMapOf<String, String>()

    forEachPart { part ->
        try {
            when (part) {
                is PartData.FileItem -> if (part.name == "file") {
                    originalFileName = part.originalFileName
                    tempFile = Files.createTempFile("sensor-", "-${originalFileName ?: "upload.csv"}")

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

                is PartData.FormItem -> if (part.name in OVERRIDE_FIELDS) {
                    fields[part.name!!] = part.value
                }

                else -> {}
            }
        } finally {
            part.dispose()
        }
    }

    val file = tempFile ?: return null
    return SensorMultipart(
        file = file,
        originalFileName = originalFileName,
        patientId = fields["patientId"],
        task = fields["task"],
        sensor = fields["sensor"],
    )
}

private val OVERRIDE_FIELDS = setOf("patientId", "task", "sensor")
