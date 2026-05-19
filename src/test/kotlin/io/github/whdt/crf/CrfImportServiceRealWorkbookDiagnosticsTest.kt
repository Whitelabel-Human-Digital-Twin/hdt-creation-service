package io.github.whdt.crf

import io.github.whdt.crf.importer.CrfImportConfig
import io.github.whdt.crf.importer.CrfImportService
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrfImportServiceRealWorkbookDiagnosticsTest {

    @Test
    fun `produces diagnostics for real workbook import`() {
        val resourceName = "/CRF_MIMOSA-DT_10_pazienti_sintetici.xlsx"
        val inputStream = this::class.java.getResourceAsStream(resourceName)
        assertNotNull(inputStream, "Test resource not found: $resourceName")

        val service = CrfImportService(
            config = CrfImportConfig(
                excludedSheetNames = setOf("Sigle"),
                patientIdAliases = setOf("ID PAZIENTE"),
                visitDateAliases = setOf("Data visita")
            ),
        )

        val result = inputStream.use { service.import(it) }

        assertTrue(result.hdts.isNotEmpty(), "Expected imported HDTs")
        assertNotNull(result.report.entries)

        // This assertion is intentionally light:
        // a real workbook may contain infos/warnings, and that is acceptable.
        assertTrue(
            result.report.entries.all { it.message.isNotBlank() },
            "Expected all report messages to be non-blank"
        )

        assertTrue(result.observations.isNotEmpty(), "Expected non-empty observations from real workbook")
    }
}
