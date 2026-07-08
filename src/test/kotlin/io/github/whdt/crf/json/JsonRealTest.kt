package io.github.whdt.crf.json

import io.github.ktwinx.core.hdt.HdtId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonRealTest {
    @Test
    fun `maps HDT correctly from real JSON`() {
        val resourceName = "/NW_ChoRod.json"
        val inputString = this::class.java.getResource(resourceName)
            ?.readText()
            ?: error("Resource not found: $resourceName")
        val jsonObject = Json.parseToJsonElement(inputString).jsonObject

        val assembler = JsonDomainAssembler()
        val result = assembler.assemble(jsonObject)

        // assert only three models imported
        val modelNames = listOf(ModelNames.ROOT, ModelNames.TEMPORAL, ModelNames.NON_LINEAR)
        val actualModelNames = result.models.map { it.name.value }
        assertEquals(modelNames, actualModelNames)

        // assert models are not empty
        result.models.forEach {
            assertTrue{ it.properties.isNotEmpty() }
        }

        // assert correct HdtId
        val hdtId = HdtId("NW_ChoRod")
        assertEquals(hdtId.id, result.hdtId)
    }
}