package io.github.whdt.crf.json

import io.github.ktwinx.core.hdt.model.property.PropertyValueType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class JsonDomainAssemblerTest {

    private val fixedInstant = Instant.parse("2026-06-08T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val assembler = JsonDomainAssembler(clock = fixedClock)

    private fun minimalJson(
        id: String = "hdt-123",
        age: Int = 34,
    ) = buildJsonObject {
        put("ID", id)
        put("Age", age)
    }

    // ─── ID / hdtId ───────────────────────────────────────────────────────────

    @Test
    fun `hdtId is taken from ID field`() {
        val json = buildJsonObject {
            put("ID", "patient-42")
            put("Age", 25)
        }
        val result = assembler.assemble(json)
        assertEquals("patient-42", result.hdtId)
    }

    // ─── Root-scalar mapping ──────────────────────────────────────────────────

    @Test
    fun `non-array scalar fields map to root model`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 30)
            put("weight", 80.5)
            put("diagnosis", "stable")
        }
        val result = assembler.assemble(json)
        val rootModel = result.models.find { it.name.value == ModelNames.ROOT }
        assertNotNull(rootModel)
        val propertyNames = rootModel.properties.map { it.name.value }
        assertTrue("weight" in propertyNames)
        assertTrue("diagnosis" in propertyNames)
        assertTrue(propertyNames.none { it == "ID" || it == "Age" })
    }

    @Test
    fun `ID and Age are never included as properties`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 30)
        }
        val result = assembler.assemble(json)
        val allPropertyNames = result.properties.map { it.name.value }
        assertTrue(allPropertyNames.none { it == "ID" || it == "Age" })
    }

    // ─── Temporal / nonLinear pair expansion ─────────────────────────────────

    @Test
    fun `temporalParameters pairs map to temporal model`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 30)
            put("temporalParameters", buildJsonArray {
                addJsonArray { add("hr"); add(72) }
                addJsonArray { add("spo2"); add(98) }
            })
        }
        val result = assembler.assemble(json)
        val temporalModel = result.models.find { it.name.value == ModelNames.TEMPORAL }
        assertNotNull(temporalModel)
        val propertyNames = temporalModel.properties.map { it.name.value }
        assertEquals(listOf("hr", "spo2"), propertyNames)
    }

    @Test
    fun `nonLinearParameters pairs map to nonLinear model`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 30)
            put("nonLinearParameters", buildJsonArray {
                addJsonArray { add("riskScore"); add(0.42) }
            })
        }
        val result = assembler.assemble(json)
        val nonLinearModel = result.models.find { it.name.value == ModelNames.NON_LINEAR }
        assertNotNull(nonLinearModel)
        assertEquals(listOf("riskScore"), nonLinearModel.properties.map { it.name.value })
    }

    @Test
    fun `always produces exactly three models root temporal nonLinear`() {
        val result = assembler.assemble(minimalJson())
        val modelNames = result.models.map { it.name.value }
        assertEquals(3, result.models.size)
        assertTrue(ModelNames.ROOT in modelNames)
        assertTrue(ModelNames.TEMPORAL in modelNames)
        assertTrue(ModelNames.NON_LINEAR in modelNames)
    }

    // ─── Ignored arrays ───────────────────────────────────────────────────────

    @Test
    fun `other array fields are ignored`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 30)
            put("someIgnoredArray", buildJsonArray { add(1); add(2); add(3) })
        }
        val result = assembler.assemble(json)
        val allPropertyNames = result.properties.map { it.name.value }
        assertTrue("someIgnoredArray" !in allPropertyNames)
    }

    // ─── propertyId format hdtId:model:key ───────────────────────────────────

    @Test
    fun `propertyId format is hdtId colon modelName colon key for root`() {
        val json = buildJsonObject {
            put("ID", "hdt-123")
            put("Age", 34)
            put("weight", 80.5)
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "weight" }
        assertNotNull(prop)
        assertEquals("hdt-123:${ModelNames.ROOT}:weight", prop.id.value)
    }

    @Test
    fun `propertyId format is hdtId colon modelName colon key for temporal`() {
        val json = buildJsonObject {
            put("ID", "hdt-123")
            put("Age", 34)
            put("temporalParameters", buildJsonArray {
                addJsonArray { add("hr"); add(72) }
            })
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "hr" }
        assertNotNull(prop)
        assertEquals("hdt-123:${ModelNames.TEMPORAL}:hr", prop.id.value)
    }

    @Test
    fun `propertyId format is hdtId colon modelName colon key for nonLinear`() {
        val json = buildJsonObject {
            put("ID", "hdt-123")
            put("Age", 34)
            put("nonLinearParameters", buildJsonArray {
                addJsonArray { add("riskScore"); add(0.42) }
            })
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "riskScore" }
        assertNotNull(prop)
        assertEquals("hdt-123:${ModelNames.NON_LINEAR}:riskScore", prop.id.value)
    }

    // ─── declaredType inference ───────────────────────────────────────────────

    @Test
    fun `string value infers STRING declaredType`() {
        val json = buildJsonObject {
            put("ID", "hdt-1"); put("Age", 30)
            put("diagnosis", "stable")
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "diagnosis" }!!
        assertEquals(PropertyValueType.STRING, prop.declaredType)
    }

    @Test
    fun `integer value infers INT declaredType`() {
        val json = buildJsonObject {
            put("ID", "hdt-1"); put("Age", 30)
            put("count", 5)
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "count" }!!
        assertEquals(PropertyValueType.INT, prop.declaredType)
    }

    @Test
    fun `decimal value infers DOUBLE declaredType`() {
        val json = buildJsonObject {
            put("ID", "hdt-1"); put("Age", 30)
            put("score", 0.42)
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "score" }!!
        assertEquals(PropertyValueType.DOUBLE, prop.declaredType)
    }

    @Test
    fun `boolean value infers BOOLEAN declaredType`() {
        val json = buildJsonObject {
            put("ID", "hdt-1"); put("Age", 30)
            put("active", true)
        }
        val result = assembler.assemble(json)
        val prop = result.properties.find { it.name.value == "active" }!!
        assertEquals(PropertyValueType.BOOLEAN, prop.declaredType)
    }

    // ─── Age into observation metadata ────────────────────────────────────────

    @Test
    fun `Age value is written into every observation metadata`() {
        val json = buildJsonObject {
            put("ID", "hdt-1")
            put("Age", 42)
            put("weight", 70)
            put("temporalParameters", buildJsonArray {
                addJsonArray { add("hr"); add(80) }
            })
        }
        val result = assembler.assemble(json)
        assertTrue(result.observations.isNotEmpty())
        result.observations.forEach { obs ->
            assertEquals("42", obs.metadata["age"], "Expected age=42 in metadata for ${obs.propertyName.value}")
        }
    }

    @Test
    fun `observation timestamp equals clock instant`() {
        val json = buildJsonObject {
            put("ID", "hdt-1"); put("Age", 30)
            put("weight", 70)
        }
        val result = assembler.assemble(json)
        result.observations.forEach { obs ->
            assertEquals(fixedInstant, obs.timestamp)
        }
    }

    // ─── Missing ID / Age → 400 ───────────────────────────────────────────────

    @Test
    fun `missing ID throws IllegalArgumentException`() {
        val json = buildJsonObject { put("Age", 30) }
        assertFailsWith<IllegalArgumentException> {
            assembler.assemble(json)
        }
    }

    @Test
    fun `missing Age throws IllegalArgumentException`() {
        val json = buildJsonObject { put("ID", "hdt-1") }
        assertFailsWith<IllegalArgumentException> {
            assembler.assemble(json)
        }
    }

    @Test
    fun `missing ID error message mentions ID`() {
        val json = buildJsonObject { put("Age", 30) }
        val ex = assertFailsWith<IllegalArgumentException> { assembler.assemble(json) }
        assertTrue(ex.message?.contains("ID") == true, "Expected error message to mention 'ID'")
    }

    @Test
    fun `missing Age error message mentions Age`() {
        val json = buildJsonObject { put("ID", "hdt-1") }
        val ex = assertFailsWith<IllegalArgumentException> { assembler.assemble(json) }
        assertTrue(ex.message?.contains("Age") == true, "Expected error message to mention 'Age'")
    }

    // ─── Full example from spec ───────────────────────────────────────────────

    @Test
    fun `full spec example assembles correctly`() {
        val json = buildJsonObject {
            put("ID", "hdt-123")
            put("Age", 34)
            put("temporalParameters", buildJsonArray {
                addJsonArray { add("hr"); add(72) }
                addJsonArray { add("spo2"); add(98) }
            })
            put("nonLinearParameters", buildJsonArray {
                addJsonArray { add("riskScore"); add(0.42) }
            })
            put("weight", 80.5)
            put("diagnosis", "stable")
            put("someIgnoredArray", buildJsonArray { add(1); add(2); add(3) })
        }
        val result = assembler.assemble(json)

        assertEquals("hdt-123", result.hdtId)
        assertEquals(3, result.models.size)

        val rootProps = result.models.find { it.name.value == ModelNames.ROOT }!!.properties
        assertEquals(setOf("weight", "diagnosis"), rootProps.map { it.name.value }.toSet())

        val temporalProps = result.models.find { it.name.value == ModelNames.TEMPORAL }!!.properties
        assertEquals(listOf("hr", "spo2"), temporalProps.map { it.name.value })

        val nonLinearProps = result.models.find { it.name.value == ModelNames.NON_LINEAR }!!.properties
        assertEquals(listOf("riskScore"), nonLinearProps.map { it.name.value })

        assertEquals(5, result.observations.size) // weight, diagnosis, hr, spo2, riskScore
        result.observations.forEach { obs ->
            assertEquals("34", obs.metadata["age"])
        }
    }
}
