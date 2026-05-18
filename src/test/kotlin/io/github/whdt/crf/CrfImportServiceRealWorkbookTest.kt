package io.github.whdt.crf

import io.github.whdt.core.hdt.model.property.PropertyId
import io.github.whdt.crf.importer.CrfImportConfig
import io.github.whdt.crf.importer.CrfImportService
import kotlin.test.*

class CrfImportServiceRealWorkbookTest {

    @Test
    fun `imports real CRF workbook from test resources`() {
        val resourceName = "/CRF_MIMOSA-DT_10_pazienti_sintetici.xlsx"
        val inputStream = this::class.java.getResourceAsStream(resourceName)
        assertNotNull(inputStream, "Test resource not found: $resourceName")

        val service = CrfImportService(
            config = CrfImportConfig(
                excludedSheetNames = setOf("Sigle"),
                patientIdAliases = setOf(
                    "ID PAZIENTE",
                    "Patient ID"
                ),
                visitDateAliases = setOf(
                    "Data visita",
                    "Visit date"
                )
            ),
        )

        val result = inputStream.use { service.import(it) }

        // Basic sanity checks
        assertTrue(result.hdts.isNotEmpty(), "Expected at least one imported HDT")

        // The synthetic workbook you shared contains patients P001..P010
        assertEquals(10, result.hdts.size, "Expected 10 HDTs from the synthetic CRF")

        val p001 = result.hdts.find { it.hdtId.toString() == "P001" }
        assertNotNull(p001, "Expected patient P001 to be imported")

        // Baseline should normally exist in this workbook
        assertTrue(
            p001.models.any { it.name.value == "baseline" },
            "Expected patient P001 to contain a baseline model"
        )

        // The excluded sheet should not become a model
        assertFalse(
            p001.models.any { it.name.value == "sigle" },
            "Excluded sheet 'Sigle' should not be imported as a model"
        )

        // Check that model IDs follow the agreed convention
        val baselineModel = p001.models.find { it.name.value == "baseline" }
        assertNotNull(baselineModel, "Expected baseline model for P001")
        assertEquals("P001:baseline", baselineModel.id.value)

        // Check that at least one property exists in baseline
        assertTrue(
            baselineModel.properties.isNotEmpty(),
            "Expected baseline model to contain properties"
        )

        // Check property id naming convention on at least one property
        val firstProperty = baselineModel.properties.first()
        assertTrue(
            firstProperty.id.value.startsWith("P001:baseline:"),
            "Expected property id to start with 'P001:baseline:'"
        )

        // We do not want structural fatal errors on a known-good workbook
        assertFalse(
            result.report.hasErrors,
            "Did not expect structural import errors for the reference CRF workbook"
        )

        val metaModel = p001.models.find { it.name.value == "meta" }
        val deltaAge = metaModel?.properties?.find { it.name.value == "delta_age" }
        assertNotNull(deltaAge, "Did not expect meta model")
        assertTrue { deltaAge.id == PropertyId("P001:meta:delta_age") }
    }
}