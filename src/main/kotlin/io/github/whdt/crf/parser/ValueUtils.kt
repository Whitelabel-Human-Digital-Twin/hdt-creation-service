package io.github.whdt.crf.parser

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

object ValueUtils {
    fun String.toKotlinInstantOfPattern(pattern: String): Instant? {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        val value = this.trim()

        return try {
            val date = LocalDate.parse(value, formatter)
            date.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toKotlinInstant()
        } catch (_: Exception) {
            null
        }
    }
}