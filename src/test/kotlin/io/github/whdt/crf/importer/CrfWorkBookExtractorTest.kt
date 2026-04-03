package io.github.whdt.crf.importer

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import kotlin.test.Test

class CrfWorkbookExtractorTest {

    @Test
    fun `extracts non empty sheets rows and cells`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Baseline")

        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("ID PAZIENTE")
        header.createCell(1).setCellValue("Data visita")
        header.createCell(2).setCellValue("Peso")

        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("P001")
        row.createCell(1).setCellValue("01/01/2025")
        row.createCell(2).setCellValue("2.5")

        val bytes = workbook.use { wb ->
            java.io.ByteArrayOutputStream().use { out ->
                wb.write(out)
                out.toByteArray()
            }
        }

        val extractor = CrfWorkbookExtractor()
        val result = bytes.inputStream().use { extractor.extract(it) }

        assertEquals(1, result.sheets.size)
        assertEquals("Baseline", result.sheets.first().originalName)
        assertEquals(2, result.sheets.first().rows.size)

        val extractedHeader = result.sheets.first().rows.first()
        assertEquals("ID PAZIENTE", extractedHeader.cells[0])

        val extractedRow = result.sheets.first().rows[1]
        assertEquals("P001", extractedRow.cells[0])
        assertEquals("2.5", extractedRow.cells[2])
    }

    @Test
    fun `skips blank rows`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Baseline")

        val header = sheet.createRow(0)
        header.createCell(0).setCellValue("ID PAZIENTE")

        sheet.createRow(1) // blank row

        val row = sheet.createRow(2)
        row.createCell(0).setCellValue("P001")

        val bytes = workbook.use { wb ->
            java.io.ByteArrayOutputStream().use { out ->
                wb.write(out)
                out.toByteArray()
            }
        }

        val extractor = CrfWorkbookExtractor()
        val result = bytes.inputStream().use { extractor.extract(it) }

        assertEquals(2, result.sheets.first().rows.size)
        assertTrue(result.sheets.first().rows.none { it.rowIndex == 1 })
    }
}