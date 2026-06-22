package io.github.whdt.crf.json

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.ModelDescription
import io.github.ktwinx.core.hdt.model.ModelId
import io.github.ktwinx.core.hdt.model.ModelName
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.core.hdt.model.property.PropertyDescription
import io.github.ktwinx.core.hdt.model.property.PropertyName
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.github.ktwinx.core.hdt.model.property.PropertyValue.Companion.pv
import io.github.ktwinx.core.hdt.model.property.valueType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

class JsonDomainAssembler(private val clock: Clock = Clock.System) {

    fun assemble(json: JsonObject): JsonImportResult {
        val hdtIdStr = extractRequiredString(json, "ID")
        val ageStr = extractRequiredNumber(json, "Age")

        val hdtId = HdtId(hdtIdStr)
        val timestamp = clock.now()
        val ageMetadata = mapOf("age" to ageStr)

        val rootModelId = ModelId("$hdtIdStr:${ModelNames.ROOT}")
        val temporalModelId = ModelId("$hdtIdStr:${ModelNames.TEMPORAL}")
        val nonLinearModelId = ModelId("$hdtIdStr:${ModelNames.NON_LINEAR}")

        val rootPairs = mutableListOf<Pair<String, JsonPrimitive>>()
        val temporalPairs = mutableListOf<Pair<String, JsonPrimitive>>()
        val nonLinearPairs = mutableListOf<Pair<String, JsonPrimitive>>()

        for ((key, value) in json) {
            when {
                key == "ID" -> continue
                key == "temporalParameters" -> value.jsonArray.forEach { item ->
                    val arr = item.jsonArray
                    temporalPairs.add(arr[0].jsonPrimitive.content to arr[1].jsonPrimitive)
                }
                key == "nonLinearParameters" -> value.jsonArray.forEach { item ->
                    val arr = item.jsonArray
                    nonLinearPairs.add(arr[0].jsonPrimitive.content to arr[1].jsonPrimitive)
                }
                value is JsonPrimitive -> rootPairs.add(key to value)
                value is JsonArray -> { /* ignored */ }
                else -> { /* ignored */ }
            }
        }

        val (rootProperties, rootObservations) = buildPropertiesAndObservations(
            rootPairs, rootModelId, hdtId, ModelName(ModelNames.ROOT), timestamp, ageMetadata,
        )
        val (temporalProperties, temporalObservations) = buildPropertiesAndObservations(
            temporalPairs, temporalModelId, hdtId, ModelName(ModelNames.TEMPORAL), timestamp, ageMetadata,
        )
        val (nonLinearProperties, nonLinearObservations) = buildPropertiesAndObservations(
            nonLinearPairs, nonLinearModelId, hdtId, ModelName(ModelNames.NON_LINEAR), timestamp, ageMetadata,
        )

        val rootModel = Model(
            hdtId = hdtId,
            name = ModelName(ModelNames.ROOT),
            description = ModelDescription("Root scalar properties from JSON ingestion"),
            properties = rootProperties,
        )
        val temporalModel = Model(
            hdtId = hdtId,
            name = ModelName(ModelNames.TEMPORAL),
            description = ModelDescription("Temporal parameters from JSON ingestion"),
            properties = temporalProperties,
        )
        val nonLinearModel = Model(
            hdtId = hdtId,
            name = ModelName(ModelNames.NON_LINEAR),
            description = ModelDescription("Non-linear parameters from JSON ingestion"),
            properties = nonLinearProperties,
        )

        return JsonImportResult(
            hdtId = hdtIdStr,
            models = listOf(rootModel, temporalModel, nonLinearModel),
            properties = rootProperties + temporalProperties + nonLinearProperties,
            observations = rootObservations + temporalObservations + nonLinearObservations,
        )
    }

    private fun buildPropertiesAndObservations(
        pairs: List<Pair<String, JsonPrimitive>>,
        modelId: ModelId,
        hdtId: HdtId,
        modelName: ModelName,
        timestamp: Instant,
        ageMetadata: Map<String, String>,
    ): Pair<List<Property>, List<PropertyObservation>> {
        val properties = pairs.map { (key, primitive) ->
            val value = primitive.toPropertyValue()
            Property(
                modelId = modelId,
                name = PropertyName(key),
                description = PropertyDescription("JSON field '$key'"),
                declaredType = value.valueType(),
                initialValue = value,
            )
        }
        val observations = properties.map { property ->
            PropertyObservation(
                hdtId = hdtId,
                modelId = modelId,
                modelName = modelName,
                propertyId = property.id,
                propertyName = property.name,
                timestamp = timestamp,
                value = property.initialValue!!,
                metadata = ageMetadata,
            )
        }
        return properties to observations
    }

    private fun extractRequiredString(json: JsonObject, key: String): String {
        val el = json[key] ?: throw IllegalArgumentException("Missing required field: $key")
        return (el as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Field '$key' must be a primitive value")
    }

    private fun extractRequiredNumber(json: JsonObject, key: String): String {
        val el = json[key] ?: throw IllegalArgumentException("Missing required field: $key")
        val primitive = (el as? JsonPrimitive)
            ?: throw IllegalArgumentException("Field '$key' must be a number")
        if (primitive.isString) throw IllegalArgumentException("Field '$key' must be a number, not a string")
        return primitive.content
    }

    private fun JsonPrimitive.toPropertyValue(): PropertyValue {
        if (isString) return content.pv()
        booleanOrNull?.let { return it.pv() }
        intOrNull?.let { return it.pv() }
        longOrNull?.let { return it.pv() }
        doubleOrNull?.let { return it.pv() }
        return content.pv()
    }
}
