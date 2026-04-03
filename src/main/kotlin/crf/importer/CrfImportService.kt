package com.example.com.crf.importer

import com.example.com.crf.CrfDomainAssembler
import com.example.com.crf.importer.model.CrfImportResult
import com.example.com.crf.importer.model.ImportLogEntry
import com.example.com.crf.importer.model.ImportReport
import com.example.com.crf.importer.model.ParsedVisitRow
import com.example.com.crf.interpreter.CrfSheetInterpreter
import java.io.InputStream

class CrfImportService(
    config: CrfImportConfig = CrfImportConfig(),
) {
    private val extractor = CrfWorkbookExtractor()
    private val interpreter = CrfSheetInterpreter(config)
    private val assembler = CrfDomainAssembler()

    fun import(inputStream: InputStream): CrfImportResult {
        val rawWorkbook = extractor.extract(inputStream)

        val logs = mutableListOf<ImportLogEntry>()
        val parsedRows = mutableListOf<ParsedVisitRow>()

        for (sheet in rawWorkbook.sheets) {
            val result = interpreter.interpret(sheet)
            logs += result.logEntries
            parsedRows += result.visitRows
        }

        val hdts = assembler.assemble(parsedRows)

        return CrfImportResult(
            hdts = hdts,
            report = ImportReport(entries = logs)
        )
    }
}