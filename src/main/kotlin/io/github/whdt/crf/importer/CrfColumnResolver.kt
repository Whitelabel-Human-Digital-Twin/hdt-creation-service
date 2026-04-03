package io.github.whdt.crf.importer

import io.github.whdt.crf.importer.model.ImportLogEntry
import io.github.whdt.crf.importer.model.ImportSeverity


class ColumnResolver(
    exactAliases: Set<String>,
    private val patterns: List<(String) -> Boolean> = emptyList()
) {

    private val normalizedAliases =
        exactAliases.map { CrfNameNormalizer.normalize(it) }.toSet()

    data class Result(
        val columnIndex: Int?,
        val logs: List<ImportLogEntry>
    )

    fun resolve(
        headerMap: Map<Int, String>,
        sheetName: String,
        headerRowIndex: Int,
        columnRole: String
    ): Result {
        val logs = mutableListOf<ImportLogEntry>()

        val normalizedHeaders = headerMap.mapValues {
            CrfNameNormalizer.normalize(it.value)
        }

        // 1. Exact alias match
        val exactMatches = normalizedHeaders.filter { (_, h) ->
            h in normalizedAliases
        }

        if (exactMatches.isNotEmpty()) {
            if (exactMatches.size > 1) {
                logs += ImportLogEntry(
                    severity = ImportSeverity.WARNING,
                    sheet = sheetName,
                    row = headerRowIndex + 1,
                    message = "Multiple $columnRole columns found: ${exactMatches.values}. Using first."
                )
            }
            return Result(exactMatches.keys.first(), logs)
        }

        // 2. Pattern fallback
        val patternMatches = normalizedHeaders.filter { (_, h) ->
            patterns.any { it(h) }
        }

        if (patternMatches.isNotEmpty()) {
            if (patternMatches.size > 1) {
                logs += ImportLogEntry(
                    severity = ImportSeverity.WARNING,
                    sheet = sheetName,
                    row = headerRowIndex + 1,
                    message = "Multiple $columnRole pattern matches found: ${patternMatches.values}. Using first."
                )
            }
            return Result(patternMatches.keys.first(), logs)
        }

        // 3. Not found
        logs += ImportLogEntry(
            severity = ImportSeverity.WARNING,
            sheet = sheetName,
            row = headerRowIndex + 1,
            message = "No $columnRole column detected"
        )

        return Result(null, logs)
    }
}
