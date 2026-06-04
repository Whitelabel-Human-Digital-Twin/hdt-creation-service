package io.github.whdt.crf.parser

import io.github.ktwinx.core.hdt.model.property.PropertyValue
import io.github.ktwinx.core.hdt.model.property.PropertyValue.Companion.pv

class CrfValueParser {

    fun parse(rawValue: String): PropertyValue {
        val value = rawValue.trim()
        if (value.isBlank()) {
            throw IllegalArgumentException("Blank values should not be parsed into PropertyValue")
        }

        val lower = value.lowercase()

        if (lower == "true" || lower == "false") {
            return lower.toBoolean().pv()
        }

        value.toIntOrNull()?.let { return it.pv() }
        value.toLongOrNull()?.let { return it.pv() }

        val normalizedDecimal = value.replace(',', '.')
        normalizedDecimal.toDoubleOrNull()?.let { return it.pv() }

        return value.pv()
    }
}