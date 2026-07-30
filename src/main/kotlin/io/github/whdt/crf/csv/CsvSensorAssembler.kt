package io.github.whdt.crf.csv

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.core.hdt.model.property.PropertyDescription
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue.Companion.pv
import io.github.ktwinx.core.hdt.model.property.PropertyValueType
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The domain output of ingesting one sensor CSV: a (shell) HDT carrying the
 * single sensor model, plus one observation per column per frame.
 */
data class CsvSensorImportResult(
    val hdt: HumanDigitalTwin,
    val observations: List<PropertyObservation>,
)

/**
 * Turns a parsed sensor CSV (one subject × one sensor) into HDT domain objects.
 *
 * - Builds a [Model] named after the sensor (e.g. `acc`) on HDT `<patientId>`.
 * - One [Property] per column, `declaredType = DOUBLE`, column name used verbatim.
 * - Each data row is a frame; for frame `f` it emits one [PropertyObservation] per
 *   column with `value` = the cell, `timestamp` = a monotonic `ingestBase + f`
 *   (strictly increasing so history stays ordered), and
 *   `metadata = { "task": task, "frame": f }`.
 */
class CsvSensorAssembler(private val clock: Clock = Clock.System) {

    fun assemble(identifiers: SensorIdentifiers, parsed: ParsedSensorCsv): CsvSensorImportResult {
        val hdtId = HdtId(identifiers.patientId)
        // ModelName enforces non-blank / no ':'; a bad sensor name surfaces here.
        val modelName = ModelName(identifiers.sensor)
        val modelId = ModelId("${identifiers.patientId}:${identifiers.sensor}")

        val properties = parsed.columns.mapIndexed { columnIndex, column ->
            Property(
                modelId = modelId,
                name = PropertyName(column),
                description = PropertyDescription("Sensor '${identifiers.sensor}' channel '$column'"),
                declaredType = PropertyValueType.DOUBLE,
                // Seed the model with the first frame's value for this channel, if any.
                initialValue = parsed.frames.firstOrNull()?.get(columnIndex)?.pv(),
                ordinal = columnIndex,
            )
        }

        val model = Model(
            hdtId = hdtId,
            name = modelName,
            description = ModelDescription("Sensor '${identifiers.sensor}' time series ingested from CSV"),
            properties = properties,
            tags = SensorModelTags.forSensorCsv,
        )

        val hdt = HumanDigitalTwin(hdtId = hdtId, models = listOf(model))

        val ingestBase = clock.now()
        val observations = ArrayList<PropertyObservation>(parsed.frames.size * properties.size)
        parsed.frames.forEachIndexed { frameIndex, frame ->
            // Strictly increasing per frame keeps the persisted history ordered.
            val timestamp = ingestBase + frameIndex.milliseconds
            val metadata = mapOf(
                "task" to identifiers.task,
                "frame" to frameIndex.toString(),
            )
            properties.forEachIndexed { columnIndex, property ->
                observations += PropertyObservation(
                    hdtId = hdtId,
                    modelId = modelId,
                    modelName = modelName,
                    propertyId = property.id,
                    propertyName = property.name,
                    timestamp = timestamp,
                    value = frame[columnIndex].pv(),
                    metadata = metadata,
                )
            }
        }

        return CsvSensorImportResult(hdt = hdt, observations = observations)
    }
}
