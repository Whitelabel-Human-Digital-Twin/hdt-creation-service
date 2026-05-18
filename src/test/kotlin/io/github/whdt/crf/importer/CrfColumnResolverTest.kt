package io.github.whdt.crf.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrfColumnResolverTest {

    @Test
    fun `resolves exact alias match`() {
        val resolver = ColumnResolver(
            exactAliases = setOf("ID PAZIENTE")
        )

        val headers = mapOf(
            0 to "Peso",
            1 to "ID PAZIENTE",
            2 to "Data visita"
        )

        val result = resolver.resolve(
            headerMap = headers,
            sheetName = "Baseline",
            headerRowIndex = 0,
            columnRole = "patient_id"
        )

        assertEquals(1, result.columnIndex)
        assertTrue(result.logs.isEmpty())
    }

    /*@Test
    fun `resolves pattern match when no exact alias exists`() {
        val resolver = ColumnResolver(
            exactAliases = setOf("Data visita"),
            patterns = listOf({ it.startsWith("data_") })
        )

        val headers = mapOf(
            0 to "ID PAZIENTE",
            1 to "Data ricovero"
        )

        val result = resolver.resolve(
            headerMap = headers,
            sheetName = "TIN",
            headerRowIndex = 0,
            columnRole = "date"
        )

        assertEquals(1, result.columnIndex)
    }*/

    @Test
    fun `warns when multiple exact matches are found`() {
        val resolver = ColumnResolver(
            exactAliases = setOf("Data visita", "Visit date")
        )

        val headers = mapOf(
            0 to "Data visita",
            1 to "Visit date"
        )

        val result = resolver.resolve(
            headerMap = headers,
            sheetName = "Sheet1",
            headerRowIndex = 0,
            columnRole = "date"
        )

        assertEquals(0, result.columnIndex)
        assertTrue(result.logs.any { it.message.contains("Multiple date columns found") })
    }

    /*@Test
    fun `warns when multiple pattern matches are found`() {
        val resolver = ColumnResolver(
            exactAliases = emptySet(),
            patterns = listOf { it.startsWith("data_") }
        )

        val headers = mapOf(
            0 to "Data ricovero",
            1 to "Data dimissione"
        )

        val result = resolver.resolve(
            headerMap = headers,
            sheetName = "Sheet1",
            headerRowIndex = 0,
            columnRole = "date"
        )

        assertEquals(0, result.columnIndex)
        assertTrue(result.logs.any { it.message.contains("Multiple date pattern matches found") })
    }*/

    @Test
    fun `warns when no match is found`() {
        val resolver = ColumnResolver(
            exactAliases = setOf("ID PAZIENTE")
        )

        val headers = mapOf(
            0 to "Peso",
            1 to "Altezza"
        )

        val result = resolver.resolve(
            headerMap = headers,
            sheetName = "Baseline",
            headerRowIndex = 0,
            columnRole = "patient_id"
        )

        assertEquals(null, result.columnIndex)
        assertTrue(result.logs.any { it.message.contains("No patient_id column detected") })
    }
}