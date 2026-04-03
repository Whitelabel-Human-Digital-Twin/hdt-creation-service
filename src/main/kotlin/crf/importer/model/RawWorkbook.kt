package com.example.com.crf.importer.model

data class RawWorkbook(
    val sheets: List<RawSheet>
)

data class RawSheet(
    val originalName: String,
    val rows: List<RawRow>
)

data class RawRow(
    val rowIndex: Int,
    val cells: Map<Int, String>
)