package io.github.whdt.crf.json

import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

class JsonArrayImporterTest {

    private val fixedInstant = Instant.parse("2026-06-25T10:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val importer = JsonArrayImporter(JsonDomainAssembler(fixedClock))

    private fun validElement(id: String, age: Int = 30) = buildJsonObject {
        put("ID", id)
        put("Age", age)
        put("weight", 70.0)
    }

    // ─── EmptyInput ───────────────────────────────────────────────────────────

    @Test
    fun `empty array returns EmptyInput`() {
        val outcome = importer.import(JsonArray(emptyList()))
        assertEquals(BatchImportOutcome.EmptyInput, outcome)
    }

    // ─── Success paths ────────────────────────────────────────────────────────

    @Test
    fun `single valid element returns Success with one HDT`() {
        val array = buildJsonArray { add(validElement("hdt-1")) }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.Success>(outcome)
        assertEquals(1, outcome.hdts.size)
        assertEquals(listOf("hdt-1"), outcome.hdtIds)
    }

    @Test
    fun `multiple valid elements return Success with all HDTs in order`() {
        val array = buildJsonArray {
            add(validElement("hdt-1"))
            add(validElement("hdt-2"))
            add(validElement("hdt-3"))
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.Success>(outcome)
        assertEquals(3, outcome.hdts.size)
        assertEquals(listOf("hdt-1", "hdt-2", "hdt-3"), outcome.hdtIds)
    }

    @Test
    fun `success observations are flattened from all elements`() {
        val array = buildJsonArray {
            add(validElement("hdt-1"))
            add(validElement("hdt-2"))
        }
        val outcome = importer.import(array) as BatchImportOutcome.Success
        val singleResult = JsonDomainAssembler(fixedClock).assemble(validElement("x"))
        assertEquals(singleResult.observations.size * 2, outcome.observations.size)
    }

    @Test
    fun `observation timestamps equal the injected fixed clock instant`() {
        val array = buildJsonArray { add(validElement("hdt-1")) }
        val outcome = importer.import(array) as BatchImportOutcome.Success
        outcome.observations.forEach { obs ->
            assertEquals(fixedInstant, obs.timestamp)
        }
    }

    @Test
    fun `duplicate IDs both pass through as Success`() {
        val array = buildJsonArray {
            add(validElement("same-id"))
            add(validElement("same-id"))
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.Success>(outcome)
        assertEquals(2, outcome.hdts.size)
        assertEquals(listOf("same-id", "same-id"), outcome.hdtIds)
    }

    // ─── ElementFailure paths ─────────────────────────────────────────────────

    @Test
    fun `array element that is a JsonPrimitive returns ElementFailure with correct index`() {
        val array = buildJsonArray {
            add(validElement("hdt-1"))
            add(JsonPrimitive("not-an-object"))
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.ElementFailure>(outcome)
        assertEquals(1, outcome.index)
    }

    @Test
    fun `array element that is a nested JsonArray returns ElementFailure with correct index`() {
        val array = buildJsonArray {
            add(buildJsonArray { add(1); add(2) })
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.ElementFailure>(outcome)
        assertEquals(0, outcome.index)
    }

    @Test
    fun `element missing ID returns ElementFailure whose reason mentions ID`() {
        val array = buildJsonArray {
            add(buildJsonObject { put("Age", 30) })
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.ElementFailure>(outcome)
        assertEquals(0, outcome.index)
        assertTrue(outcome.reason.contains("ID"), "Expected 'ID' in reason: ${outcome.reason}")
    }

    @Test
    fun `element missing Age returns ElementFailure whose reason mentions Age`() {
        val array = buildJsonArray {
            add(buildJsonObject { put("ID", "hdt-1") })
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.ElementFailure>(outcome)
        assertEquals(0, outcome.index)
        assertTrue(outcome.reason.contains("Age"), "Expected 'Age' in reason: ${outcome.reason}")
    }

    @Test
    fun `fail-fast - valid then bad then valid returns ElementFailure for the bad index`() {
        val array = buildJsonArray {
            add(validElement("hdt-1"))
            add(buildJsonObject { put("Age", 30) }) // missing ID
            add(validElement("hdt-3"))
        }
        val outcome = importer.import(array)
        assertIs<BatchImportOutcome.ElementFailure>(outcome)
        assertEquals(1, outcome.index)
    }
}
