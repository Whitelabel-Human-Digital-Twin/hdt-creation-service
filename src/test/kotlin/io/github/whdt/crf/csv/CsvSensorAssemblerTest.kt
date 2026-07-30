package io.github.whdt.crf.csv

import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.core.hdt.model.property.PropertyValueType
import io.github.ktwinx.distributed.serde.Stub
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CsvSensorAssemblerTest {

    private val fixedInstant = Instant.parse("2026-07-16T00:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val assembler = CsvSensorAssembler(clock = fixedClock)

    private val ids = SensorIdentifiers(patientId = "01A101", task = "nw", sensor = "acc")

    private fun sampleCsv(frames: Int = 3): ParsedSensorCsv {
        val columns = listOf("sens1_x", "sens1_y", "sens1_z")
        val rows = (0 until frames).map { f ->
            listOf(f.toDouble(), f + 0.1, f + 0.2)
        }
        return ParsedSensorCsv(columns = columns, frames = rows)
    }

    @Test
    fun `model is named after the sensor`() {
        val result = assembler.assemble(ids, sampleCsv())
        assertEquals(1, result.hdt.models.size)
        assertEquals("acc", result.hdt.models.single().name.value)
    }

    @Test
    fun `hdt id is the patient id`() {
        val result = assembler.assemble(ids, sampleCsv())
        assertEquals("01A101", result.hdt.hdtId.id)
    }

    @Test
    fun `one property per column with verbatim names and DOUBLE type`() {
        val model = assembler.assemble(ids, sampleCsv()).hdt.models.single()
        assertEquals(listOf("sens1_x", "sens1_y", "sens1_z"), model.properties.map { it.name.value })
        assertTrue(model.properties.all { it.declaredType == PropertyValueType.DOUBLE })
    }

    @Test
    fun `emits one observation per column per frame`() {
        val result = assembler.assemble(ids, sampleCsv(frames = 3))
        assertEquals(3 * 3, result.observations.size)
    }

    @Test
    fun `every observation carries task and frame metadata`() {
        val result = assembler.assemble(ids, sampleCsv(frames = 2))
        result.observations.forEach { obs ->
            assertEquals("nw", obs.metadata["task"])
        }
        // frame 0 appears for all three columns, then frame 1.
        val framesForFirstColumn = result.observations
            .filter { it.propertyName.value == "sens1_x" }
            .map { it.metadata["frame"] }
        assertEquals(listOf("0", "1"), framesForFirstColumn)
    }

    @Test
    fun `observation values match the cells`() {
        val result = assembler.assemble(ids, sampleCsv(frames = 2))
        val xValues = result.observations.filter { it.propertyName.value == "sens1_x" }.map { it.value }
        // frame 0 -> 0.0, frame 1 -> 1.0 (from sampleCsv)
        assertEquals(0.0.pvDouble(), xValues[0].pvDouble())
        assertEquals(1.0.pvDouble(), xValues[1].pvDouble())
    }

    @Test
    fun `timestamps are strictly increasing across frames and equal within a frame`() {
        val result = assembler.assemble(ids, sampleCsv(frames = 4))
        val perFrameTimestamps = result.observations
            .groupBy { it.metadata["frame"] }
            .mapValues { (_, obs) -> obs.map { it.timestamp }.toSet() }

        // All observations within one frame share a single timestamp.
        perFrameTimestamps.forEach { (_, timestamps) -> assertEquals(1, timestamps.size) }

        // Frame timestamps strictly increase with frame index.
        val ordered = (0 until 4).map { f -> perFrameTimestamps[f.toString()]!!.single() }
        for (i in 1 until ordered.size) {
            assertTrue(ordered[i] > ordered[i - 1], "frame $i timestamp must exceed frame ${i - 1}")
        }
        // Base is the clock instant.
        assertEquals(fixedInstant, ordered[0])
    }

    @Test
    fun `spec example yields 9 properties and frames times 9 observations`() {
        val columns = (1..3).flatMap { s -> listOf("sens${s}_x", "sens${s}_y", "sens${s}_z") }
        val frameCount = 3309
        val frames = (0 until frameCount).map { f -> columns.indices.map { it + f.toDouble() } }
        val parsed = ParsedSensorCsv(columns = columns, frames = frames)

        val result = assembler.assemble(ids, parsed)

        assertEquals(9, result.hdt.models.single().properties.size)
        assertEquals(frameCount * 9, result.observations.size)
        assertTrue(result.observations.all { it.metadata["task"] == "nw" })
    }

    @Test
    fun `assembled model is tagged with sensorCsv origin`() {
        val result = assembler.assemble(ids, sampleCsv())
        assertEquals(mapOf("origin" to "sensorCsv"), result.hdt.models.single().tags)
    }

    @Test
    fun `origin tag survives Stub hdtJson encode-decode round trip`() {
        val result = assembler.assemble(ids, sampleCsv())

        val encoded = Stub.hdtJson.encodeToString(result.hdt)
        val decoded = Stub.hdtJson.decodeFromString<HumanDigitalTwin>(encoded)

        assertEquals(mapOf("origin" to "sensorCsv"), decoded.models.single().tags)
    }

    @Test
    fun `property ordinals equal the column index and match the columns order`() {
        val model = assembler.assemble(ids, sampleCsv()).hdt.models.single()
        assertEquals(listOf(0, 1, 2), model.properties.map { it.ordinal })
    }

    @Test
    fun `a sensor name containing a colon is rejected`() {
        val bad = ids.copy(sensor = "acc:1")
        assertFailsWith<IllegalArgumentException> {
            assembler.assemble(bad, sampleCsv())
        }
    }

    // Helper to compare PropertyValue doubles without depending on its toString.
    private fun io.github.ktwinx.core.hdt.model.property.PropertyValue.pvDouble(): Double =
        (this as io.github.ktwinx.core.hdt.model.property.PropertyValue.DoublePropertyValue).value

    private fun Double.pvDouble(): Double = this
}
