package io.github.whdt.crf.json

import io.github.ktwinx.core.hdt.HdtId
import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.core.hdt.model.property.PropertyObservation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

sealed interface BatchImportOutcome {
    data class Success(
        val hdts: List<HumanDigitalTwin>,
        val observations: List<PropertyObservation>,
        val hdtIds: List<String>,
    ) : BatchImportOutcome

    /** Zero-based index of the first rejected element + the reason. */
    data class ElementFailure(val index: Int, val reason: String) : BatchImportOutcome

    /** The array was empty. */
    data object EmptyInput : BatchImportOutcome
}

class JsonArrayImporter(
    private val assembler: JsonDomainAssembler = JsonDomainAssembler(),
) {
    fun import(array: JsonArray): BatchImportOutcome {
        if (array.isEmpty()) return BatchImportOutcome.EmptyInput

        val hdts = mutableListOf<HumanDigitalTwin>()
        val observations = mutableListOf<PropertyObservation>()
        val hdtIds = mutableListOf<String>()

        array.forEachIndexed { index, element ->
            val obj = element as? JsonObject
                ?: return BatchImportOutcome.ElementFailure(index, "expected a JSON object")
            val result = try {
                assembler.assemble(obj)
            } catch (e: IllegalArgumentException) {
                return BatchImportOutcome.ElementFailure(index, e.message ?: "invalid element")
            }
            hdts += HumanDigitalTwin(HdtId(result.hdtId), result.models)
            observations += result.observations
            hdtIds += result.hdtId
        }
        return BatchImportOutcome.Success(hdts, observations, hdtIds)
    }
}
