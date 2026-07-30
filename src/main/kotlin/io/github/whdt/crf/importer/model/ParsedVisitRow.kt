package io.github.whdt.crf.importer.model

import kotlin.time.Instant

data class ParsedVisitRow(
    val patientId: String,
    val originalSheetName: String,
    val modelName: String,
    val modelId: String,
    val timestamp: Instant?,
    val sourceRowIndex: Int,
    val properties: List<ParsedPropertyCell>,
)

data class ParsedPropertyCell(
    val originalHeader: String,
    val propertyName: String,
    val propertyId: String,
    val rawValue: String,
    val columnIndex: Int,
)