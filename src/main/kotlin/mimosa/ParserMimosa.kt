package com.example.com.mimosa


import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import java.nio.file.Files
import java.nio.file.Path

object ParserMimosa {
    private val patientIdRegex = Regex("^P\\d{3}$")

    private fun normalizeSheetName(s: String) = s.trim()

    private fun Workbook.getSheetByTrimmedName(name: String): Sheet? {
        val target = normalizeSheetName(name)
        return (0 until numberOfSheets)
            .map { getSheetAt(it) }
            .firstOrNull { normalizeSheetName(it.sheetName) == target }
    }

    private fun Sheet.headerMap(headerRowIndex0: Int, fmt: DataFormatter): Map<String, Int> {
        val row = getRow(headerRowIndex0) ?: return emptyMap()
        val map = linkedMapOf<String, Int>()
        for (c in 0 until row.lastCellNum.toInt().coerceAtLeast(0)) {
            val h = fmt.formatCellValue(row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim()
            if (h.isNotEmpty()) map[h] = c
        }
        return map
    }

    private fun Sheet.findFirstDataRowIndex0(
        startAfterRow0: Int,
        patientIdColumnIndex0: Int,
        fmt: DataFormatter
    ): Int? {
        for (r in (startAfterRow0 + 1)..lastRowNum) {
            val row = getRow(r) ?: continue
            val cell = row.getCell(patientIdColumnIndex0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue
            val v = fmt.formatCellValue(cell).trim()
            if (patientIdRegex.matches(v)) return r
        }
        return null
    }

    private fun Sheet.isRowEmpty(row: Row?, fmt: DataFormatter): Boolean {
        if (row == null) return true
        val max = row.lastCellNum.toInt().coerceAtLeast(0)
        for (c in 0 until max) {
            val cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue
            if (fmt.formatCellValue(cell).trim().isNotEmpty()) return false
        }
        return true
    }

    fun parseMimosaWorkbook(path: Path): WorkbookResult {
        Files.newInputStream(path).use { input ->
            XSSFWorkbook(input).use { wb ->
                val fmt = DataFormatter()
                val allSheetResults = mutableListOf<SheetResult>()
                val workbookErrors = mutableListOf<CellError>()

                for (spec in mimosaSpecs) {
                    val sheet = wb.getSheetByTrimmedName(spec.name)
                    if (sheet == null) {
                        workbookErrors += CellError(spec.name, 0, "", "Missing sheet")
                        continue
                    }

                    val headers = sheet.headerMap(spec.headerRowIndex0, fmt)
                    val pidCol = headers[spec.patientIdHeader]
                    if (pidCol == null) {
                        allSheetResults += SheetResult(
                            sheet = sheet.sheetName,
                            rows = emptyList(),
                            errors = listOf(CellError(sheet.sheetName, spec.headerRowIndex0 + 1, spec.patientIdHeader, "Missing header"))
                        )
                        continue
                    }

                    val firstDataRow0 = sheet.findFirstDataRowIndex0(spec.headerRowIndex0, pidCol, fmt)
                    if (firstDataRow0 == null) {
                        allSheetResults += SheetResult(sheet.sheetName, emptyList(), emptyList())
                        continue
                    }

                    val rows = mutableListOf<Map<String, String>>()
                    for (r in firstDataRow0 until sheet.lastRowNum) {
                        val row = sheet.getRow(r)
                        if (sheet.isRowEmpty(row, fmt)) continue

                        val pid = fmt.formatCellValue(row.getCell(pidCol, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim()
                        if (!patientIdRegex.matches(pid)) continue // skip legend/notes rows safely

                        val map = linkedMapOf<String, String>()
                        for ((h, c) in headers) {
                            val cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                            map[h] = cell?.let { fmt.formatCellValue(it).trim() } ?: ""
                        }
                        rows += map
                    }

                    allSheetResults += SheetResult(sheet.sheetName, rows, emptyList())
                }

                return WorkbookResult(
                    sheets = allSheetResults,
                    errors = workbookErrors
                )
            }
        }
    }
}