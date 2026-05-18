package io.github.whdt.crf.interpreter

import io.github.whdt.crf.importer.CrfImportConfig
import io.github.whdt.crf.importer.model.RawRow
import io.github.whdt.crf.importer.model.RawSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrfSheetInterpreterTest {

    private fun interpreter(
        excluded: Set<String> = emptySet()
    ) = CrfSheetInterpreter(
        CrfImportConfig(
            excludedSheetNames = excluded,
            patientIdAliases = setOf("ID PAZIENTE", "Patient ID"),
            visitDateAliases = setOf("Data visita", "Visit date")
        )
    )

    @Test
    fun `excludes configured sheet`() {
        val interpreter = interpreter(excluded = setOf("Sigle"))

        val sheet = RawSheet(
            originalName = "Sigle",
            rows = listOf(
                RawRow(0, mapOf(0 to "Codice", 1 to "Descrizione"))
            )
        )

        val result = interpreter.interpret(sheet)

        assertTrue(result.visitRows.isEmpty())
        assertTrue(result.logEntries.any { it.message.contains("excluded") })
    }

    @Test
    fun `detects header row not in first row`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Baseline",
            rows = listOf(
                RawRow(0, mapOf(0 to "Legenda", 1 to "Valori")),
                RawRow(1, mapOf(0 to "ID PAZIENTE", 1 to "Data visita", 2 to "Peso")),
                RawRow(2, mapOf(0 to "P001", 1 to "01/01/2025", 2 to "2.5"))
            )
        )

        val result = interpreter.interpret(sheet)

        assertEquals(1, result.visitRows.size)
        assertEquals("P001", result.visitRows.first().patientId)
    }

    @Test
    fun `keeps latest duplicate patient row`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Baseline",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Data visita", 2 to "Peso")),
                RawRow(1, mapOf(0 to "P001", 1 to "01/01/2025", 2 to "2.5")),
                RawRow(2, mapOf(0 to "P001", 1 to "02/01/2025", 2 to "2.7"))
            )
        )

        val result = interpreter.interpret(sheet)

        assertEquals(1, result.visitRows.size)
        val row = result.visitRows.first()
        assertEquals("P001", row.patientId)
        assertEquals("2.7", row.properties.first().rawValue)
        assertTrue(result.logEntries.any { it.message.contains("Duplicate patient row") })
    }

    @Test
    fun `omits patient id and date from properties`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Follow up 3 mesi",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Data visita", 2 to "Peso", 3 to "Note")),
                RawRow(1, mapOf(0 to "P001", 1 to "01/04/2025", 2 to "5.2", 3 to "ok"))
            )
        )

        val result = interpreter.interpret(sheet)

        val properties = result.visitRows.first().properties
        assertEquals(2, properties.size)
        assertEquals(listOf("peso", "note"), properties.map { it.propertyName })
    }

    @Test
    fun `omits blank cells from properties`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Follow up 6 mesi",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Data visita", 2 to "Peso", 3 to "Altezza")),
                RawRow(1, mapOf(0 to "P001", 1 to "01/07/2025", 2 to "6.0"))
            )
        )

        val result = interpreter.interpret(sheet)

        val properties = result.visitRows.first().properties
        assertEquals(1, properties.size)
        assertEquals("peso", properties.first().propertyName)
    }

    /*@Test
    fun `uses pattern based date detection when alias is missing`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "TIN",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Data ricovero", 2 to "Peso")),
                RawRow(1, mapOf(0 to "P001", 1 to "03/01/2025", 2 to "2.1"))
            )
        )

        val result = interpreter.interpret(sheet)

        val row = result.visitRows.first()
        assertNotNull(row.timestamp)
    }*/

    @Test
    fun `warns when no date column is found`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Dimissione",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Peso")),
                RawRow(1, mapOf(0 to "P001", 1 to "2.8"))
            )
        )

        val result = interpreter.interpret(sheet)

        assertTrue(result.logEntries.any { it.message.contains("No date column detected") })
        assertEquals(null, result.visitRows.first().timestamp)
    }

    @Test
    fun `returns error when header row cannot be detected`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Broken Sheet",
            rows = listOf(
                RawRow(0, mapOf(0 to "Peso", 1 to "Altezza")),
                RawRow(1, mapOf(0 to "2.5", 1 to "48"))
            )
        )

        val result = interpreter.interpret(sheet)

        assertTrue(result.visitRows.isEmpty())
        assertTrue(result.logEntries.any { it.message.contains("Could not detect header row") })
    }

    @Test
    fun `normalizes model and property names`() {
        val interpreter = interpreter()

        val sheet = RawSheet(
            originalName = "Follow up 12 mesi",
            rows = listOf(
                RawRow(0, mapOf(0 to "ID PAZIENTE", 1 to "Data visita", 2 to "Peso alla visita")),
                RawRow(1, mapOf(0 to "P001", 1 to "01/01/2026", 2 to "8.4"))
            )
        )

        val result = interpreter.interpret(sheet)

        val visitRow = result.visitRows.first()
        assertEquals("follow_up_12_mesi", visitRow.modelName)
        assertEquals("P001:follow_up_12_mesi", visitRow.modelId)
        assertEquals("peso_alla_visita", visitRow.properties.first().propertyName)
        assertEquals(
            "P001:follow_up_12_mesi:peso_alla_visita",
            visitRow.properties.first().propertyId
        )
    }
}