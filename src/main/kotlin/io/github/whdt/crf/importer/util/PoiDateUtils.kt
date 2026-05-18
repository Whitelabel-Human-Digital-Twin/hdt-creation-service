package io.github.whdt.crf.importer.util

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DateUtil
import java.time.ZoneOffset
import kotlin.time.Instant

object PoiDateUtils {

    fun cellToInstantOrNull(cell: Cell?): Instant? {
        if (cell == null) return null
        return try {
            if (DateUtil.isCellDateFormatted(cell)) {
                cell.localDateTimeCellValue
                    .toInstant(ZoneOffset.UTC)
                    .let { Instant.parse(it.toString()) }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}