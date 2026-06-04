package io.github.whdt.crf.importer.model

import io.github.ktwinx.core.hdt.HumanDigitalTwin
import io.github.ktwinx.core.hdt.model.property.PropertyObservation

data class CrfImportResult(
    val hdts: List<HumanDigitalTwin>,
    val observations: List<PropertyObservation>,
    val report: ImportReport,
)
