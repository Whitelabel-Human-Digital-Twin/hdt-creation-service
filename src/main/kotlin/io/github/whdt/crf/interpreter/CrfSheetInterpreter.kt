package io.github.whdt.crf.interpreter

import io.github.whdt.crf.importer.ColumnResolver
import io.github.whdt.crf.importer.CrfImportConfig
import io.github.whdt.crf.importer.CrfNameNormalizer
import io.github.whdt.crf.importer.model.ImportLogEntry
import io.github.whdt.crf.importer.model.ImportSeverity
import io.github.whdt.crf.importer.model.ParsedPropertyCell
import io.github.whdt.crf.importer.model.ParsedVisitRow
import io.github.whdt.crf.importer.model.RawRow
import io.github.whdt.crf.importer.model.RawSheet
import kotlin.collections.get
import kotlin.time.Instant

class CrfSheetInterpreter(
    config: CrfImportConfig,
) {
    private val patientIdResolver = ColumnResolver(
        exactAliases = config.patientIdAliases
    )

    private val dateResolver = ColumnResolver(
        exactAliases = config.visitDateAliases,
        patterns = listOf(
            { it.startsWith("data_") },
            { it.startsWith("date_") }
        )
    )

    private val excludedSheetNamesNormalized =
        config.excludedSheetNames.map { CrfNameNormalizer.normalize(it) }.toSet()

    data class InterpretationResult(
        val visitRows: List<ParsedVisitRow>,
        val logEntries: List<ImportLogEntry>,
    )

    fun interpret(sheet: RawSheet): InterpretationResult {
        val logs = mutableListOf<ImportLogEntry>()

        val normalizedSheetName = CrfNameNormalizer.normalize(sheet.originalName)
        if (normalizedSheetName in excludedSheetNamesNormalized) {
            logs += ImportLogEntry(
                severity = ImportSeverity.INFO,
                sheet = sheet.originalName,
                message = "Sheet excluded by configuration"
            )
            return InterpretationResult(emptyList(), logs)
        }

        if (sheet.rows.isEmpty()) {
            logs += ImportLogEntry(
                severity = ImportSeverity.WARNING,
                sheet = sheet.originalName,
                message = "Sheet is empty"
            )
            return InterpretationResult(emptyList(), logs)
        }

        val headerRow = detectHeaderRow(sheet, patientIdResolver)
        if (headerRow == null) {
            logs += ImportLogEntry(
                severity = ImportSeverity.ERROR,
                sheet = sheet.originalName,
                message = "Could not detect header row"
            )
            return InterpretationResult(emptyList(), logs)
        }

        val headerMap = headerRow.cells.mapValues { (_, value) -> value.trim() }

        val patientIdResolution = patientIdResolver.resolve(
            headerMap = headerMap,
            sheetName = sheet.originalName,
            headerRowIndex = headerRow.rowIndex,
            columnRole = "patient_id"
        )

        logs += patientIdResolution.logs

        val patientIdColumnIndex = patientIdResolution.columnIndex

        val dateResolution = dateResolver.resolve(
            headerMap = headerMap,
            sheetName = sheet.originalName,
            headerRowIndex = headerRow.rowIndex,
            columnRole = "date"
        )

        logs += dateResolution.logs

        val visitDateColumnIndex = dateResolution.columnIndex

        val modelName = CrfNameNormalizer.normalize(sheet.originalName)

        val candidateRows = sheet.rows.filter { it.rowIndex > headerRow.rowIndex }

        val latestByPatientId = linkedMapOf<String, ParsedVisitRow>()

        for (row in candidateRows) {
            val patientId = row.cells[patientIdColumnIndex]?.trim().orEmpty()
            if (patientId.isBlank()) continue

            val timestamp = parseTimestamp(row, visitDateColumnIndex)

            val modelId = "$patientId:$modelName"

            val properties = row.cells.entries
                .asSequence()
                .filter { (colIndex, rawValue) ->
                    rawValue.isNotBlank() &&
                            colIndex != patientIdColumnIndex &&
                            colIndex != visitDateColumnIndex
                }
                .mapNotNull { (colIndex, rawValue) ->
                    val originalHeader = headerMap[colIndex]?.trim().orEmpty()
                    if (originalHeader.isBlank()) return@mapNotNull null

                    val propertyName = CrfNameNormalizer.normalize(originalHeader)
                    if (propertyName.isBlank()) return@mapNotNull null

                    ParsedPropertyCell(
                        originalHeader = originalHeader,
                        propertyName = propertyName,
                        propertyId = "$modelId:$propertyName",
                        rawValue = rawValue.trim()
                    )
                }
                .toList()

            val parsedVisitRow = ParsedVisitRow(
                patientId = patientId,
                originalSheetName = sheet.originalName,
                modelName = modelName,
                modelId = modelId,
                timestamp = timestamp,
                sourceRowIndex = row.rowIndex,
                properties = properties,
            )

            if (latestByPatientId.containsKey(patientId)) {
                logs += ImportLogEntry(
                    severity = ImportSeverity.WARNING,
                    sheet = sheet.originalName,
                    row = row.rowIndex + 1,
                    message = "Duplicate patient row for '$patientId'. Keeping latest occurrence."
                )
            }

            latestByPatientId[patientId] = parsedVisitRow
        }

        if (latestByPatientId.isEmpty()) {
            logs += ImportLogEntry(
                severity = ImportSeverity.WARNING,
                sheet = sheet.originalName,
                message = "No patient rows found after header detection"
            )
        }

        return InterpretationResult(
            visitRows = latestByPatientId.values.toList(),
            logEntries = logs
        )
    }

    private fun detectHeaderRow(
        sheet: RawSheet,
        patientIdResolver: ColumnResolver,
    ): RawRow? {
        for (row in sheet.rows) {
            val headerMap = row.cells

            val resolution = patientIdResolver.resolve(
                headerMap = headerMap,
                sheetName = sheet.originalName,
                headerRowIndex = row.rowIndex,
                columnRole = "patient_id"
            )

            if (resolution.columnIndex != null) {
                return row
            }
        }

        return null
    }

    private fun parseTimestamp(
        row: RawRow,
        visitDateColumnIndex: Int?
    ): Instant? {
        if (visitDateColumnIndex == null) return null

        val raw = row.cells[visitDateColumnIndex]?.trim().orEmpty()
        if (raw.isBlank()) return null

        return tryParseInstant(raw)
    }

    private fun tryParseInstant(raw: String): Instant? {
        val candidates = listOf(
            raw,
            "${raw}T00:00:00Z"
        )

        for (candidate in candidates) {
            try {
                return Instant.parse(candidate)
            } catch (_: Throwable) {
            }
        }

        val slashMatch = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(raw)
        if (slashMatch != null) {
            val (d, m, y) = slashMatch.destructured
            val iso = "%04d-%02d-%02dT00:00:00Z".format(y.toInt(), m.toInt(), d.toInt())
            return try {
                Instant.parse(iso)
            } catch (_: Throwable) {
                null
            }
        }

        val dashMatch = Regex("""^(\d{1,2})-(\d{1,2})-(\d{4})$""").matchEntire(raw)
        if (dashMatch != null) {
            val (d, m, y) = dashMatch.destructured
            val iso = "%04d-%02d-%02dT00:00:00Z".format(y.toInt(), m.toInt(), d.toInt())
            return try {
                Instant.parse(iso)
            } catch (_: Throwable) {
                null
            }
        }

        return null
    }
}