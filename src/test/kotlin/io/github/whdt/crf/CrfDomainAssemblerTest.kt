package io.github.whdt.crf

import io.github.whdt.crf.importer.model.ParsedPropertyCell
import io.github.whdt.crf.importer.model.ParsedVisitRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrfDomainAssemblerTest {

    private val assembler = CrfDomainAssembler()

    @Test
    fun `assembles one hdt per patient`() {
        val rows = listOf(
            ParsedVisitRow(
                patientId = "P001",
                originalSheetName = "Baseline",
                modelName = "baseline",
                modelId = "P001:baseline",
                timestamp = Instant.parse("2025-01-01T00:00:00Z"),
                sourceRowIndex = 1,
                properties = listOf(
                    ParsedPropertyCell(
                        originalHeader = "Peso",
                        propertyName = "peso",
                        propertyId = "P001:baseline:peso",
                        rawValue = "2.5",
                        columnIndex = 2,
                    )
                )
            ),
            ParsedVisitRow(
                patientId = "P001",
                originalSheetName = "Follow up 3 mesi",
                modelName = "follow_up_3_mesi",
                modelId = "P001:follow_up_3_mesi",
                timestamp = Instant.parse("2025-04-01T00:00:00Z"),
                sourceRowIndex = 2,
                properties = listOf(
                    ParsedPropertyCell(
                        originalHeader = "Altezza",
                        propertyName = "altezza",
                        propertyId = "P001:follow_up_3_mesi:altezza",
                        rawValue = "55",
                        columnIndex = 3,
                    )
                )
            )
        )

        val result = assembler.assemble(rows)

        assertEquals(1, result.hdts.size)
        val hdt = result.hdts.first()
        assertEquals("P001", hdt.hdtId.toString())
        assertEquals(2, hdt.models.size)
    }

    @Test
    fun `maps model name and id correctly`() {
        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = Instant.parse("2025-01-01T00:00:00Z"),
            sourceRowIndex = 1,
            properties = emptyList()
        )

        val result = assembler.assemble(listOf(row))
        val model = result.hdts.first().models.first()

        assertEquals("baseline", model.name.value)
        assertEquals("P001:baseline", model.id.value)
    }

    @Test
    fun `maps property name and id correctly`() {
        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = Instant.parse("2025-01-01T00:00:00Z"),
            sourceRowIndex = 1,
            properties = listOf(
                ParsedPropertyCell(
                    originalHeader = "Peso alla visita",
                    propertyName = "peso_alla_visita",
                    propertyId = "P001:baseline:peso_alla_visita",
                    rawValue = "2.7",
                    columnIndex = 2,
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val property = result.hdts.first().models.first().properties.first()

        assertEquals("peso_alla_visita", property.name.value)
        assertEquals("P001:baseline:peso_alla_visita", property.id.value)
    }

    @Test
    fun `uses parsed timestamp in observations`() {
        val ts = Instant.parse("2025-01-01T00:00:00Z")

        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = ts,
            sourceRowIndex = 1,
            properties = listOf(
                ParsedPropertyCell(
                    originalHeader = "Peso",
                    propertyName = "peso",
                    propertyId = "P001:baseline:peso",
                    rawValue = "2.5",
                    columnIndex = 2,
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val observation = result.observations.first()

        assertEquals(ts, observation.timestamp)
    }

    @Test
    fun `produces observations for each property`() {
        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = Instant.parse("2025-01-01T00:00:00Z"),
            sourceRowIndex = 1,
            properties = listOf(
                ParsedPropertyCell(
                    originalHeader = "Peso",
                    propertyName = "peso",
                    propertyId = "P001:baseline:peso",
                    rawValue = "2.5",
                    columnIndex = 2,
                ),
                ParsedPropertyCell(
                    originalHeader = "Altezza",
                    propertyName = "altezza",
                    propertyId = "P001:baseline:altezza",
                    rawValue = "50",
                    columnIndex = 3,
                )
            )
        )

        val result = assembler.assemble(listOf(row))

        assertTrue(result.observations.isNotEmpty(), "Expected non-empty observations")
        assertEquals(2, result.observations.size)
    }

    @Test
    fun `produces delta_age observation when baseline has required fields`() {
        val rows = listOf(
            ParsedVisitRow(
                patientId = "P001",
                originalSheetName = "Baseline",
                modelName = "baseline",
                modelId = "P001:baseline",
                timestamp = Instant.parse("2025-01-01T00:00:00Z"),
                sourceRowIndex = 1,
                properties = listOf(
                    ParsedPropertyCell(
                        originalHeader = "Epoca Presunta Parto",
                        propertyName = "epoca_presunta_parto",
                        propertyId = "P001:baseline:epoca_presunta_parto",
                        rawValue = "2024-03-15",
                        columnIndex = 2,
                    ),
                    ParsedPropertyCell(
                        originalHeader = "Data di Nascita",
                        propertyName = "data_di_nascita",
                        propertyId = "P001:baseline:data_di_nascita",
                        rawValue = "2024-03-20",
                        columnIndex = 3,
                    )
                )
            )
        )

        val result = assembler.assemble(rows)

        val deltaAgeObs = result.observations.find { it.propertyName.value == "delta_age" }
        assertNotNull(deltaAgeObs, "Expected delta_age observation")
        assertEquals("P001:meta", deltaAgeObs.modelId.value)
    }

    @Test
    fun `delta_age property keeps the default unassigned ordinal`() {
        val rows = listOf(
            ParsedVisitRow(
                patientId = "P001",
                originalSheetName = "Baseline",
                modelName = "baseline",
                modelId = "P001:baseline",
                timestamp = Instant.parse("2025-01-01T00:00:00Z"),
                sourceRowIndex = 1,
                properties = listOf(
                    ParsedPropertyCell(
                        originalHeader = "Epoca Presunta Parto",
                        propertyName = "epoca_presunta_parto",
                        propertyId = "P001:baseline:epoca_presunta_parto",
                        rawValue = "2024-03-15",
                        columnIndex = 2,
                    ),
                    ParsedPropertyCell(
                        originalHeader = "Data di Nascita",
                        propertyName = "data_di_nascita",
                        propertyId = "P001:baseline:data_di_nascita",
                        rawValue = "2024-03-20",
                        columnIndex = 3,
                    )
                )
            )
        )

        val result = assembler.assemble(rows)

        val metaModel = result.hdts.first().models.find { it.name.value == "meta" }
        assertNotNull(metaModel)
        val deltaAgeProperty = metaModel.properties.find { it.name.value == "delta_age" }
        assertNotNull(deltaAgeProperty)
        assertEquals(-1, deltaAgeProperty.ordinal)
    }

    @Test
    fun `property initialValue matches parsed cell value`() {
        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = Instant.parse("2025-01-01T00:00:00Z"),
            sourceRowIndex = 1,
            properties = listOf(
                ParsedPropertyCell(
                    originalHeader = "Peso",
                    propertyName = "peso",
                    propertyId = "P001:baseline:peso",
                    rawValue = "42",
                    columnIndex = 2,
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val property = result.hdts.first().models.first().properties.first()

        assertNotNull(property.initialValue, "Expected non-null initialValue")
    }

    @Test
    fun `property ordinal equals the source cell column index`() {
        val row = ParsedVisitRow(
            patientId = "P001",
            originalSheetName = "Baseline",
            modelName = "baseline",
            modelId = "P001:baseline",
            timestamp = Instant.parse("2025-01-01T00:00:00Z"),
            sourceRowIndex = 1,
            properties = listOf(
                ParsedPropertyCell(
                    originalHeader = "Peso",
                    propertyName = "peso",
                    propertyId = "P001:baseline:peso",
                    rawValue = "2.5",
                    columnIndex = 2,
                ),
                ParsedPropertyCell(
                    originalHeader = "Altezza",
                    propertyName = "altezza",
                    propertyId = "P001:baseline:altezza",
                    rawValue = "50",
                    columnIndex = 4,
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val properties = result.hdts.first().models.first().properties

        assertEquals(2, properties.find { it.name.value == "peso" }!!.ordinal)
        assertEquals(4, properties.find { it.name.value == "altezza" }!!.ordinal)
    }
}
