package com.example.com.mimosa

import io.github.whdt.core.hdt.HdtId
import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.ModelDescription
import io.github.whdt.core.hdt.model.ModelId
import io.github.whdt.core.hdt.model.ModelName
import io.github.whdt.core.hdt.model.property.Property
import io.github.whdt.core.hdt.model.property.PropertyDescription
import io.github.whdt.core.hdt.model.property.PropertyName
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.core.hdt.model.property.PropertyValue.Companion.pv
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object Mapper {
    private const val PATIENT_ID_HEADER = "ID PAZIENTE"

    private fun normalizeKey(s: String): String =
        s.trim()
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_\\-]"), "")

    private fun sheetToModelId(patientId: String, sheetName: String): ModelId {
        val nPatient = normalizeKey(patientId)
        val nModel = normalizeKey(sheetName)
        return ModelId("$nPatient:$nModel")
    }

    private fun toPropertyValue(raw: String): PropertyValue {
        val s = raw.trim()
        if (s.isEmpty()) return PropertyValue.EmptyPropertyValue

        val lower = s.lowercase()
        if (lower == "true" || lower == "false") {
            return lower.toBoolean().pv()
        }

        // common in CRFs: 0/1 used as boolean
        if (s == "0" || s == "1") {
            return (s == "1").pv()
        }

        s.toIntOrNull()?.let { return it.pv() }
        s.toLongOrNull()?.let { return it.pv() }

        // handle commas as decimal separators if needed
        val normalized = s.replace(',', '.')
        normalized.toDoubleOrNull()?.let { return it.pv() }

        return s.pv()
    }

    private fun String.toKotlinInstant(): Instant? {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val value = this.trim()

        return try {
            println("Parsing date string: '$value'")
            val date = LocalDate.parse(value, formatter)
            println("Parsed LocalDate: $date")
            date.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toKotlinInstant()
        } catch (e: Exception) {
            println("Failed to parse '$value': ${e.message}")
            null
        }
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
            val hdtId = makeHdtId(normalizeKey(patientId))
            // group the patient’s rows by sheet, then turn each sheet into a model
            val models: List<Model> =
                sheetRows
                    .groupBy { it.sheet }
                    .map { (sheetName, rowsInSheet) ->
                        val modelId = sheetToModelId(patientId, sheetName)
                        val merged: Map<String, String> =
                            rowsInSheet.fold(emptyMap()) { acc, sr -> acc + sr.row }

                        val propertiesRaw = merged
                            .filterKeys { it.trim().isNotEmpty() }
                            .filterKeys { it.trim() != PATIENT_ID_HEADER } // don’t turn patient id into a property

                        val time = propertiesRaw
                            .entries
                            .firstOrNull { normalizeKey(it.key).startsWith("data_", ignoreCase = true) }
                            ?.value
                            ?.also { println("Matched date value: '$it'") }
                            ?.toKotlinInstant()
                            ?: now

                        val properties = propertiesRaw.map { (colName, rawValue) ->
                            Property(
                                name = PropertyName(normalizeKey(colName)),
                                modelId = modelId,
                                description = PropertyDescription("From sheet '$sheetName', column '$colName'"),
                                timestamp = time,
                                value = toPropertyValue(rawValue)
                            )
                        }

                        Model(
                            hdtId = hdtId,
                            name = ModelName(normalizeKey(sheetName)),
                            description = ModelDescription("Imported from Excel sheet '$sheetName'"),
                            properties = properties
                        )
                    }
                    .sortedBy { it.id.value } // stable ordering

            HumanDigitalTwin(
                hdtId = hdtId,
                models = models
            )
        }.sortedBy { it.hdtId.toString() }
    }
}