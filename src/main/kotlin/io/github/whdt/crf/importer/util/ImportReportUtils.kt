package io.github.whdt.crf.importer.util

import io.github.whdt.crf.importer.model.ImportReport
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ImportLoggingUtils {

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun createLogDir(baseDir: Path = Path.of("logs")): Path {
        Files.createDirectories(baseDir)
        return baseDir
    }

    fun timestamp(): String = LocalDateTime.now().format(formatter)

    fun reportToText(report: ImportReport): String {
        val builder = StringBuilder()

        builder.appendLine("=== IMPORT REPORT ===")
        builder.appendLine("Entries: ${report.entries.size}")
        builder.appendLine()

        report.entries.forEach { entry ->
            builder.append("[${entry.severity}] ")

            entry.sheet?.let { builder.append("Sheet=$it ") }
            entry.row?.let { builder.append("Row=$it ") }
            entry.column?.let { builder.append("Column=$it ") }

            builder.appendLine("- ${entry.message}")
        }

        builder.appendLine()
        builder.appendLine("Summary:")
        builder.appendLine(
            report.entries.groupBy { it.severity }
                .map { (k, v) -> "$k=${v.size}" }
                .joinToString(", ")
        )

        return builder.toString()
    }
}