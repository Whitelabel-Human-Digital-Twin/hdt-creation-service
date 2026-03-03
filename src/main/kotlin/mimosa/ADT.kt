package com.example.com.mimosa

data class CellError(
    val sheet: String,
    val row: Int,      // 1-based for humans
    val column: String,
    val message: String
)

data class SheetResult(
    val sheet: String,
    val rows: List<Map<String, String>>,
    val errors: List<CellError>
)

data class WorkbookResult(
    val sheets: List<SheetResult>,
    val errors: List<CellError>
)