package io.github.whdt.crf.csv

/**
 * The three identifiers that describe a sensor CSV: one subject, one task, one sensor.
 *
 * They are derived from the upload filename `<patientId>_<task>_<sensor>.csv`
 * (e.g. `01A101_nw_acc`), but any of them may be overridden by an explicit
 * multipart form field, because the customer warned that filename conventions
 * may change over time.
 */
data class SensorIdentifiers(
    val patientId: String,
    val task: String,
    val sensor: String,
)

object SensorCsvNaming {

    /**
     * Parses `<patientId>_<task>_<sensor>.csv` into its three tokens.
     *
     * Returns `null` for each token that cannot be derived (missing filename,
     * wrong number of `_`-separated tokens, or a blank token). Callers are
     * expected to fall back to explicit form fields for anything missing.
     */
    fun parseFilename(fileName: String?): Triple<String?, String?, String?> {
        if (fileName.isNullOrBlank()) return Triple(null, null, null)

        val base =
            if (fileName.length >= 4 && fileName.substring(fileName.length - 4).equals(".csv", ignoreCase = true))
                fileName.substring(0, fileName.length - 4)
            else fileName

        val parts = base.split("_")
        return if (parts.size == 3 && parts.all { it.isNotBlank() })
            Triple(parts[0], parts[1], parts[2])
        else
            Triple(null, null, null)
    }

    /**
     * Resolves the final identifiers, letting non-blank form-field overrides win
     * over whatever was parsed from the filename.
     *
     * @throws IllegalArgumentException if any identifier is still missing after
     *   applying overrides.
     */
    fun resolve(
        fileName: String?,
        patientIdOverride: String?,
        taskOverride: String?,
        sensorOverride: String?,
    ): SensorIdentifiers {
        val (parsedPatientId, parsedTask, parsedSensor) = parseFilename(fileName)

        val patientId = patientIdOverride?.takeIf { it.isNotBlank() } ?: parsedPatientId
        val task = taskOverride?.takeIf { it.isNotBlank() } ?: parsedTask
        val sensor = sensorOverride?.takeIf { it.isNotBlank() } ?: parsedSensor

        require(!patientId.isNullOrBlank()) {
            "Missing 'patientId': could not parse it from the filename '$fileName' and no 'patientId' form field was provided"
        }
        require(!task.isNullOrBlank()) {
            "Missing 'task': could not parse it from the filename '$fileName' and no 'task' form field was provided"
        }
        require(!sensor.isNullOrBlank()) {
            "Missing 'sensor': could not parse it from the filename '$fileName' and no 'sensor' form field was provided"
        }

        return SensorIdentifiers(patientId, task, sensor)
    }
}
