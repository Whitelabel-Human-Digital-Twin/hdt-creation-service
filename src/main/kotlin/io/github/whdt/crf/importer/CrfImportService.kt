package io.github.whdt.crf.importer

import io.github.whdt.crf.importer.CrfWorkbookExtractor
import io.github.whdt.crf.CrfDomainAssembler
import io.github.whdt.crf.importer.model.CrfImportResult
import io.github.whdt.crf.importer.model.ImportLogEntry
import io.github.whdt.crf.importer.model.ImportReport
import io.github.whdt.crf.importer.model.ParsedVisitRow
import io.github.whdt.crf.interpreter.CrfSheetInterpreter
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