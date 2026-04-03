package io.github.whdt.crf

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
import io.github.whdt.crf.importer.model.ParsedVisitRow
import io.github.whdt.crf.parser.CrfValueParser
import io.github.whdt.crf.parser.ValueUtils.toKotlinInstantOfPattern
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

class CrfDomainAssembler(
    private val valueParser: CrfValueParser = CrfValueParser(),
) {

    fun assemble(parsedRows: List<ParsedVisitRow>): List<HumanDigitalTwin> {
        val rowsByPatient = parsedRows.groupBy { it.patientId }

        return rowsByPatient.entries
            .map { (patientId, rows) ->
                val hdtId = HdtId(patientId)

                val models = rows
                    .sortedBy { it.modelName }
                    .map { row -> row.toModel() }

                HumanDigitalTwin(
                    hdtId = hdtId,
                    models = models,
                )
            }
            .map { fillMetadata(it) }
            .sortedBy { it.hdtId.toString() }
    }

    private fun ParsedVisitRow.toModel(): Model {
        val ts = timestamp ?: Clock.System.now()

        return Model(
            hdtId = HdtId(patientId),
            name = ModelName(modelName),
            description = ModelDescription("Imported from sheet '$originalSheetName', row ${sourceRowIndex + 1}"),
            properties = properties.map { propertyCell ->
                Property(
                    name = PropertyName(propertyCell.propertyName),
                    description = PropertyDescription("Imported from column '${propertyCell.originalHeader}' in sheet '$originalSheetName'"),
                    timestamp = ts,
                    value = valueParser.parse(propertyCell.rawValue),
                    modelId = ModelId("$patientId:$modelName"),
                    metadata = emptyMap(),
                )
            }
        )
    }

    private fun fillMetadata(hdt: HumanDigitalTwin): HumanDigitalTwin {
        fun PropertyValue.unwrapInstant(): Instant? {
            return when (this) {
                is PropertyValue.StringPropertyValue -> this.value.toKotlinInstantOfPattern("yyyy-MM-dd")
                else -> null
            }
        }
        val baselineModel = hdt.models.find { it.name.value == "baseline" }
        if (baselineModel == null) { return hdt }
        val expectedBirth = baselineModel
            .properties
            .find { it.name.value == "epoca_presunta_parto" }
            ?.value
            ?.unwrapInstant()
        if (expectedBirth == null) { return hdt }
        val actualBirth = baselineModel
            .properties
            .find { it.name.value == "data_di_nascita" }
            ?.value
            ?.unwrapInstant()
        if (actualBirth == null) { return hdt }
        val deltaAge = expectedBirth - actualBirth
        return hdt.copy(
            metadata = mapOf(
                "delta_age" to deltaAge.toDouble(DurationUnit.DAYS).toString(),
            )
        )
    }
}