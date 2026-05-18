package io.github.whdt.crf

import io.github.whdt.crf.importer.model.ParsedPropertyCell
import io.github.whdt.crf.importer.model.ParsedVisitRow
import kotlin.test.Test
import kotlin.test.assertEquals
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
                        rawValue = "2.5"
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
                        rawValue = "55"
                    )
                )
            )
        )

        val result = assembler.assemble(rows)

        assertEquals(1, result.size)
        val hdt = result.first()
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
        val model = result.first().models.first()

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
                    rawValue = "2.7"
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val property = result.first().models.first().properties.first()

        assertEquals("peso_alla_visita", property.name.value)
        assertEquals("P001:baseline:peso_alla_visita", property.id.value)
    }

    @Test
    fun `uses parsed timestamp when available`() {
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
                    rawValue = "2.5"
                )
            )
        )

        val result = assembler.assemble(listOf(row))
        val property = result.first().models.first().properties.first()

        assertEquals(ts, property.timestamp)
    }
}