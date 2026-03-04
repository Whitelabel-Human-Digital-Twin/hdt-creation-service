package com.example.com.mimosa

import kotlinx.serialization.Serializable

@Serializable
data class CellError(
    val sheet: String,
    val row: Int,      // 1-based for humans
    val column: String,
    val message: String
)

@Serializable
data class SheetResult(
    val sheet: String,
    val rows: List<Map<String, String>>,
    val errors: List<CellError>
)

@Serializable
data class WorkbookResult(
    val sheets: List<SheetResult>,
    val errors: List<CellError>
)