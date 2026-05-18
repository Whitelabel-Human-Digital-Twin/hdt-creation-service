package io.github.whdt.crf.importer

data class CrfImportConfig(
    val excludedSheetNames: Set<String> = emptySet(),
    val patientIdAliases: Set<String> = setOf(
        "id paziente",
        "id_paziente",
        "patient id",
        "patient_id",
        "id patient"
    ),
    val visitDateAliases: Set<String> = setOf(
        "data visita",
        "data_visit",
        "data del controllo",
        "data controllo",
        "visit date",
        "visit_date",
        "date",
        "data"
    ),
)
