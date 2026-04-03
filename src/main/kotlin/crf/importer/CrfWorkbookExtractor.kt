package com.example.com.crf.importer

import com.example.com.crf.importer.model.RawRow
import com.example.com.crf.importer.model.RawSheet
import com.example.com.crf.importer.model.RawWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

class CrfWorkbookExtractor {

    fun extract(inputStream: InputStream): RawWorkbook {
        XSSFWorkbook(inputStream).use { workbook ->
            val formatter = DataFormatter()

            val sheets = (0 until workbook.numberOfSheets).map { index ->
                val sheet = workbook.getSheetAt(index)

                val rows = (sheet.firstRowNum..sheet.lastRowNum).mapNotNull { rowIndex ->
                    val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
                    val lastCellNum = row.lastCellNum.toInt().coerceAtLeast(0)

                    val cells = (0 until lastCellNum)
                        .mapNotNull { columnIndex ->
                            val cell = row.getCell(columnIndex)
                            val value = cell?.let { formatter.formatCellValue(it) }?.trim().orEmpty()
                            if (value.isBlank()) null else columnIndex to value
                        }
                        .toMap()

                    if (cells.isEmpty()) null else RawRow(rowIndex = rowIndex, cells = cells)
                }

                RawSheet(
                    originalName = sheet.sheetName,
                    rows = rows
                )
            }

            return RawWorkbook(sheets = sheets)
        }
    }
}