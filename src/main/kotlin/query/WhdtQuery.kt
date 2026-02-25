package com.example.com.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AggregateOperation {
    @SerialName("avg")
    AVERAGE,
    @SerialName("min")
    MINIMUM,
    @SerialName("max")
    MAXIMUM,
}

@Serializable
enum class FilterOperator {
    @SerialName("<")
    LESS,
    @SerialName("=")
    EQUALS,
    @SerialName("<=")
    LESS_OR_EQUALS,
    @SerialName(">")
    GREATER,
    @SerialName(">=")
    GREATER_OR_EQUALS,
}

@Serializable
data class QueryFilter(
    val propertyName: String,
    val op: FilterOperator,
    val value: String,
)

@Serializable
sealed interface WhdtQuery

@Serializable
data class AggregateQuery(
    val operation: AggregateOperation,
    val property: String,
    val filters: ArrayList<QueryFilter>,
    val dts: ArrayList<String>,
): WhdtQuery