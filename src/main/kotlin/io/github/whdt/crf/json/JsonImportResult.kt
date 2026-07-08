package io.github.whdt.crf.json

import io.github.ktwinx.core.hdt.model.Model
import io.github.ktwinx.core.hdt.model.property.Property
import io.github.ktwinx.core.hdt.model.property.PropertyObservation

object ModelNames {
    const val ROOT = "info"
    const val TEMPORAL = "temporal"
    const val NON_LINEAR = "nonLinear"
}

data class JsonImportResult(
    val hdtId: String,
    val models: List<Model>,
    val properties: List<Property>,
    val observations: List<PropertyObservation>,
)
