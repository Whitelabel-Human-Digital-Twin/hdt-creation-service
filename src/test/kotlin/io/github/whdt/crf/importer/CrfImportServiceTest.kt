package io.github.whdt.crf.importer

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrfImportServiceTest {

    @Test
    fun `imports workbook into hdts and report`() {
        val workbook = XSSFWorkbook()

        val baseline = workbook.createSheet("Baseline")
        val baselineHeader = baseline.createRow(0)
        baselineHeader.createCell(0).setCellValue("ID PAZIENTE")
        baselineHeader.createCell(1).setCellValue("Data visita")
        baselineHeader.createCell(2).setCellValue("Peso")

        val baselineRow = baseline.createRow(1)
        baselineRow.createCell(0).setCellValue("P001")
        baselineRow.createCell(1).setCellValue("01/01/2025")
        baselineRow.createCell(2).setCellValue("2.5")

        val followUp = workbook.createSheet("Follow up 3 mesi")
        val followHeader = followUp.createRow(0)
        followHeader.createCell(0).setCellValue("ID PAZIENTE")
        followHeader.createCell(1).setCellValue("Data visita")
        followHeader.createCell(2).setCellValue("Altezza")

        val followRow = followUp.createRow(1)
        followRow.createCell(0).setCellValue("P001")
        followRow.createCell(1).setCellValue("01/04/2025")
        followRow.createCell(2).setCellValue("55")

        val excluded = workbook.createSheet("Sigle")
        val excludedRow = excluded.createRow(0)
        excludedRow.createCell(0).setCellValue("Codice")
        excludedRow.createCell(1).setCellValue("Descrizione")

        val bytes = workbook.use { wb ->
            java.io.ByteArrayOutputStream().use { out ->
                wb.write(out)
                out.toByteArray()
            }
        }

        val service = CrfImportService(
            config = CrfImportConfig(
                excludedSheetNames = setOf("Sigle"),
                patientIdAliases = setOf("ID PAZIENTE"),
                visitDateAliases = setOf("Data visita")
            ),
        )

        val result = bytes.inputStream().use { service.import(it) }

        assertEquals(1, result.hdts.size)

        val hdt = result.hdts.first()
        assertEquals("P001", hdt.hdtId.toString())
        assertEquals(2, hdt.models.size)

        assertTrue(result.report.entries.any { it.message.contains("excluded") })
    }
}