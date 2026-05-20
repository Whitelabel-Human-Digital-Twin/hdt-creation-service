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
import io.github.whdt.core.hdt.model.property.PropertyObservation
import io.github.whdt.core.hdt.model.property.PropertyValue
import io.github.whdt.core.hdt.model.property.PropertyValue.Companion.pv
import io.github.whdt.core.hdt.model.property.PropertyValueType
import io.github.whdt.core.hdt.model.property.valueType
import io.github.whdt.crf.importer.model.ParsedVisitRow
import io.github.whdt.crf.parser.CrfValueParser
import io.github.whdt.crf.parser.ValueUtils.toKotlinInstantOfPattern
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant

class CrfDomainAssembler(
    private val valueParser: CrfValueParser = CrfValueParser(),
) {

    data class AssembleResult(
        val hdts: List<HumanDigitalTwin>,
        val observations: List<PropertyObservation>,
    )

    private data class ModelWithObservations(
        val model: Model,
        val observations: List<PropertyObservation>,
    )

    fun assemble(parsedRows: List<ParsedVisitRow>): AssembleResult {
        val rowsByPatient = parsedRows.groupBy { it.patientId }
        val allObservations = mutableListOf<PropertyObservation>()

        val hdts = rowsByPatient.entries
            .map { (patientId, rows) ->
                val hdtId = HdtId(patientId)

                val modelsWithObs = rows
                    .sortedBy { it.modelName }
                    .map { row -> row.toModelWithObservations() }

                allObservations += modelsWithObs.flatMap { it.observations }

                HumanDigitalTwin(
                    hdtId = hdtId,
                    models = modelsWithObs.map { it.model },
                )
            }
            .map { fillMetadata(it, allObservations) }
            .sortedBy { it.hdtId.toString() }

        return AssembleResult(hdts = hdts, observations = allObservations)
    }

    private fun ParsedVisitRow.toModelWithObservations(): ModelWithObservations {
        val ts = timestamp ?: Clock.System.now()
        val hdtId = HdtId(patientId)
        val modelId = ModelId("$patientId:$modelName")

        val properties = properties.map { cell ->
            val parsedValue = valueParser.parse(cell.rawValue)
            Property(
                modelId = modelId,
                name = PropertyName(cell.propertyName),
                description = PropertyDescription("Imported from column '${cell.originalHeader}' in sheet '$originalSheetName'"),
                declaredType = parsedValue.valueType(),
                initialValue = parsedValue,
            )
        }

        val model = Model(
            hdtId = hdtId,
            name = ModelName(modelName),
            description = ModelDescription("Imported from sheet '$originalSheetName', row ${sourceRowIndex + 1}"),
            properties = properties,
        )

        val observations = properties.map { property ->
            PropertyObservation(
                hdtId = hdtId,
                modelId = modelId,
                modelName = ModelName(modelName),
                propertyId = property.id,
                propertyName = property.name,
                timestamp = ts,
                value = property.initialValue!!,
            )
        }

        return ModelWithObservations(model, observations)
    }

    private fun fillMetadata(hdt: HumanDigitalTwin, allObservations: MutableList<PropertyObservation>): HumanDigitalTwin {
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
            ?.initialValue
            ?.unwrapInstant()
        if (expectedBirth == null) { return hdt }
        val actualBirth = baselineModel
            .properties
            .find { it.name.value == "data_di_nascita" }
            ?.initialValue
            ?.unwrapInstant()
        if (actualBirth == null) { return hdt }
        val deltaAge = (expectedBirth - actualBirth).toInt(DurationUnit.DAYS)
        val metaModelId = ModelId("${hdt.hdtId}:meta")
        val deltaAgeProperty = Property(
            modelId = metaModelId,
            name = PropertyName("delta_age"),
            description = PropertyDescription("Number of days between expected and actual birth"),
            declaredType = PropertyValueType.INT,
            initialValue = deltaAge.pv(),
        )
        allObservations += PropertyObservation(
            hdtId = hdt.hdtId,
            modelId = metaModelId,
            modelName = ModelName("meta"),
            propertyId = deltaAgeProperty.id,
            propertyName = PropertyName("delta_age"),
            timestamp = actualBirth,
            value = deltaAge.pv(),
        )
        return hdt.copy(
            models = hdt.models + Model(
                hdtId = hdt.hdtId,
                name = ModelName("meta"),
                description = ModelDescription("Automatically derived properties"),
                properties = listOf(deltaAgeProperty)
            )
        )
    }
}
