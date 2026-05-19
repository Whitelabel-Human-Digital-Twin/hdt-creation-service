package io.github.whdt.crf.importer.model

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.model.property.PropertyObservation

data class CrfImportResult(
    val hdts: List<HumanDigitalTwin>,
    val observations: List<PropertyObservation>,
    val report: ImportReport,
)
