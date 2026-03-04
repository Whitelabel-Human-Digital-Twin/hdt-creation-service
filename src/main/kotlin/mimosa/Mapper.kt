package com.example.com.mimosa

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlin.time.Clock
import kotlin.time.Instant

object Mapper {
    private const val PATIENT_ID_HEADER = "ID PAZIENTE"

    private fun normalizeKey(s: String): String =
        s.trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_\\-]"), "")

    private fun sheetToModelId(sheetName: String): String =
        normalizeKey(sheetName)

    private fun columnToPropertyId(sheetName: String, columnName: String): String =
        "${sheetToModelId(sheetName)}:${normalizeKey(columnName)}"

    private fun toPropertyValue(raw: String): PropertyValue {
        val s = raw.trim()
        if (s.isEmpty()) return PropertyValue.EmptyPropertyValue

        val lower = s.lowercase()
        if (lower == "true" || lower == "false") {
            return PropertyValue.BooleanPropertyValue(lower.toBoolean())
        }

        // common in CRFs: 0/1 used as boolean
        if (s == "0" || s == "1") {
            return PropertyValue.BooleanPropertyValue(s == "1")
        }

        s.toIntOrNull()?.let { return PropertyValue.IntPropertyValue(it) }
        s.toLongOrNull()?.let { return PropertyValue.LongPropertyValue(it) }

        // handle commas as decimal separators if needed
        val normalized = s.replace(',', '.')
        normalized.toDoubleOrNull()?.let { return PropertyValue.DoublePropertyValue(it) }

        return PropertyValue.StringPropertyValue(s)
    }

    fun workbookResultToHDTs(
        workbook: WorkbookResult,
        now: Instant = Clock.System.now(),
        makeHdtId: (String) -> HdtId,
    ): List<HumanDigitalTwin> {

        // 1) group rows by patient across all sheets
        data class SheetRow(val sheet: String, val row: Map<String, String>)

        val byPatient: Map<String, List<SheetRow>> =
            workbook.sheets
                .asSequence()
                .flatMap { sheetRes ->
                    sheetRes.rows.asSequence().map { row -> SheetRow(sheetRes.sheet, row) }
                }
                .mapNotNull { sr ->
                    val pid = sr.row[PATIENT_ID_HEADER]?.trim().orEmpty()
                    if (pid.isBlank()) null else pid to sr
                }
                .groupBy({ it.first }, { it.second })

        // 2) build an HDT per patient
        return byPatient.map { (patientId, sheetRows) ->
            val hdtId = makeHdtId(patientId)

            // group the patient’s rows by sheet, then turn each sheet into a model
            val models: List<Model> =
                sheetRows
                    .groupBy { it.sheet }
                    .map { (sheetName, rowsInSheet) ->
                        // In your file, there should be 1 row per patient per sheet.
                        // If there are duplicates, we can merge (later row overwrites earlier).
                        val merged: Map<String, String> =
                            rowsInSheet.fold(emptyMap()) { acc, sr -> acc + sr.row }

                        val properties = merged
                            .filterKeys { it.trim().isNotEmpty() }
                            .filterKeys { it != PATIENT_ID_HEADER } // don’t turn patient id into a property
                            .map { (colName, rawValue) ->
                                Property(
                                    name = colName,
                                    id = columnToPropertyId(sheetName, colName),
                                    description = "From sheet '$sheetName', column '$colName'",
                                    timestamp = now,
                                    value = toPropertyValue(rawValue)
                                )
                            }

                        Model(
                            id = sheetToModelId(sheetName),
                            description = "Imported from Excel sheet '$sheetName'",
                            properties = properties
                        )
                    }
                    .sortedBy { it.id } // stable ordering

            HumanDigitalTwin(
                hdtId = hdtId,
                models = models
            )
        }.sortedBy { it.hdtId.toString() }
    }
}