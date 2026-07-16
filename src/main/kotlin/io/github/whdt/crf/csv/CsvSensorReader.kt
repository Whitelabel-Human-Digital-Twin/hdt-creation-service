package io.github.whdt.crf.csv

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStream

/**
 * A parsed sensor CSV: the verbatim column names from the header row and one
 * numeric frame per data row.
 *
 * `frames[f][c]` is the value of column `columns[c]` in frame `f`.
 */
data class ParsedSensorCsv(
    val columns: List<String>,
    val frames: List<List<Double>>,
)

/**
 * Reads a comma-separated sensor CSV with a header row and an all-numeric body.
 *
 * Column names are used verbatim (no normalization, no axis parsing); every body
 * cell must parse as a [Double].
 */
class CsvSensorReader {

    fun read(input: InputStream): ParsedSensorCsv {
        val format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build()

        input.reader(Charsets.UTF_8).use { reader ->
            CSVParser.parse(reader, format).use { parser ->
                val columns = parser.headerNames
                require(columns.isNotEmpty()) { "CSV has no header row" }

                val frames = parser.map { record ->
                    columns.map { column ->
                        val raw = record.get(column)
                        raw.toDoubleOrNull()
                            ?: throw IllegalArgumentException(
                                "Non-numeric value '$raw' in column '$column' at line ${record.recordNumber}"
                            )
                    }
                }

                return ParsedSensorCsv(columns = columns, frames = frames)
            }
        }
    }
}
