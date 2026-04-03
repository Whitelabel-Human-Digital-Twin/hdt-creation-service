package io.github.whdt.crf.importer.util

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import java.nio.file.Files
import java.nio.file.Path

suspend fun MultiPartData.readPartAsTempFile(
    fieldName: String,
    maxBytes: Long
): Path? {
    var tempFile: Path? = null

    forEachPart { part ->
        try {
            if (part is PartData.FileItem && part.name == fieldName) {
                val originalName = part.originalFileName ?: "upload.xlsx"
                tempFile = Files.createTempFile("upload-", "-$originalName")

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