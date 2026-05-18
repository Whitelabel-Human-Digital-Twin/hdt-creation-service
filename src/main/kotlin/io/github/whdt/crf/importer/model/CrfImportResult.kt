package io.github.whdt.crf.importer.model

import io.github.whdt.core.hdt.HumanDigitalTwin

data class CrfImportResult(
    val hdts: List<HumanDigitalTwin>,
    val report: ImportReport,
)