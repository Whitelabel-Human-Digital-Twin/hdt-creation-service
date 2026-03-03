package com.example.com.mimosa

data class SheetSpec(
    val name: String,              // trimmed name
    val headerRowIndex0: Int,       // 0-based
    val patientIdHeader: String     // "ID PAZIENTE"
)

val mimosaSpecs = listOf(
    SheetSpec("Baseline", 2, "ID PAZIENTE"),
    SheetSpec("Gravidanza e parto", 0, "ID PAZIENTE"),
    SheetSpec("TIN", 0, "ID PAZIENTE"),
    SheetSpec("Dimissione", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 40 settimane", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 3 mesi", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 6 mesi", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 12 mesi", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 18 mesi", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 24 mesi", 0, "ID PAZIENTE"),
    SheetSpec("Follow up 30-36 mesi", 0, "ID PAZIENTE"),
)