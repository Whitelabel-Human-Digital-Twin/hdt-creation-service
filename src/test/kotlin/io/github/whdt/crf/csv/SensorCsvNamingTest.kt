package io.github.whdt.crf.csv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensorCsvNamingTest {

    // ─── parseFilename ────────────────────────────────────────────────────────

    @Test
    fun `parses patientId task sensor from a well-formed filename`() {
        val (patientId, task, sensor) = SensorCsvNaming.parseFilename("01A101_nw_acc.csv")
        assertEquals("01A101", patientId)
        assertEquals("nw", task)
        assertEquals("acc", sensor)
    }

    @Test
    fun `strips a case-insensitive csv extension`() {
        val (patientId, task, sensor) = SensorCsvNaming.parseFilename("01A101_nw_acc.CSV")
        assertEquals("01A101", patientId)
        assertEquals("nw", task)
        assertEquals("acc", sensor)
    }

    @Test
    fun `returns nulls when the token count is wrong`() {
        val (patientId, task, sensor) = SensorCsvNaming.parseFilename("01A101_nw.csv")
        assertNull(patientId)
        assertNull(task)
        assertNull(sensor)
    }

    @Test
    fun `returns nulls for a null filename`() {
        val (patientId, task, sensor) = SensorCsvNaming.parseFilename(null)
        assertNull(patientId)
        assertNull(task)
        assertNull(sensor)
    }

    // ─── resolve ──────────────────────────────────────────────────────────────

    @Test
    fun `resolve uses filename tokens when no overrides are given`() {
        val ids = SensorCsvNaming.resolve("01A101_nw_acc.csv", null, null, null)
        assertEquals(SensorIdentifiers("01A101", "nw", "acc"), ids)
    }

    @Test
    fun `resolve lets non-blank overrides win over filename tokens`() {
        val ids = SensorCsvNaming.resolve(
            fileName = "01A101_nw_acc.csv",
            patientIdOverride = "99Z999",
            taskOverride = null,
            sensorOverride = "gyro",
        )
        assertEquals(SensorIdentifiers("99Z999", "nw", "gyro"), ids)
    }

    @Test
    fun `resolve treats blank overrides as absent`() {
        val ids = SensorCsvNaming.resolve("01A101_nw_acc.csv", "  ", "", null)
        assertEquals(SensorIdentifiers("01A101", "nw", "acc"), ids)
    }

    @Test
    fun `resolve works from form fields alone when the filename is unusable`() {
        val ids = SensorCsvNaming.resolve("weird-name.csv", "p1", "t1", "s1")
        assertEquals(SensorIdentifiers("p1", "t1", "s1"), ids)
    }

    @Test
    fun `resolve fails when patientId cannot be determined`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SensorCsvNaming.resolve("weird-name.csv", null, "t1", "s1")
        }
        assertTrue(ex.message!!.contains("patientId"))
    }

    @Test
    fun `resolve fails when task cannot be determined`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SensorCsvNaming.resolve("weird-name.csv", "p1", null, "s1")
        }
        assertTrue(ex.message!!.contains("task"))
    }

    @Test
    fun `resolve fails when sensor cannot be determined`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            SensorCsvNaming.resolve("weird-name.csv", "p1", "t1", null)
        }
        assertTrue(ex.message!!.contains("sensor"))
    }
}
