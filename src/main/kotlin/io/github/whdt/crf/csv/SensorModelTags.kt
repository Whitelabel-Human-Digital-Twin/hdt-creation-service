package io.github.whdt.crf.csv

/**
 * Tags stamped on models produced by the sensor-CSV ingest pipeline, so that
 * consumers can tell a sensor model apart from a CRF-derived one without
 * relying on the model name.
 */
object SensorModelTags {
    const val ORIGIN_KEY = "origin"
    const val ORIGIN_SENSOR_CSV = "sensorCsv"

    val forSensorCsv: Map<String, String> = mapOf(ORIGIN_KEY to ORIGIN_SENSOR_CSV)
}
