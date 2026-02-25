package com.example.com.query

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WldtQuery(val resourceType: String, val queryType: String, val startIndex: Long?, val endIndex: Long?) {

    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    companion object {

        fun getStateWithRange(start: Long, end: Long): WldtQuery {
            return WldtQuery(
                resourceType = "DIGITAL_TWIN_STATE",
                queryType = "SAMPLE_RANGE",
                startIndex = start,
                endIndex = end,
            )
        }
    }
}

fun main() {
    println(WldtQuery.getStateWithRange(0, 3).toJsonString())
}