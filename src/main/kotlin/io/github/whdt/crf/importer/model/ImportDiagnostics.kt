package io.github.whdt.crf.importer.model

enum class ImportSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class ImportLogEntry(
    val severity: ImportSeverity,
    val sheet: String? = null,
    val row: Int? = null,
    val column: String? = null,
    val message: String,
)

data class ImportReport(
    val entries: List<ImportLogEntry>
) {
    val hasErrors: Boolean get() = entries.any { it.severity == ImportSeverity.ERROR }
}