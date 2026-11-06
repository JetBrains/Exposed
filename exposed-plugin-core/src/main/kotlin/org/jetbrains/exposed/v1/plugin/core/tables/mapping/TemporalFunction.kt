package org.jetbrains.exposed.v1.plugin.core.tables.mapping

import com.squareup.kotlinpoet.ClassName
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType

/** The date-time column functions shared by all Exposed date-time modules, with the Kotlin type each module stores. */
internal enum class TemporalFunction(val function: String) {
    DATE(ColumnFunctions.DATE),
    TIME(ColumnFunctions.TIME),
    DATETIME(ColumnFunctions.DATETIME),
    TIMESTAMP_WITH_TIME_ZONE(ColumnFunctions.TIMESTAMP_WITH_TIME_ZONE),
}

private val JAVA_OFFSET_DATE_TIME: ClassName = ClassName("java.time", "OffsetDateTime")
private val JODA_DATE_TIME: ClassName = ClassName("org.joda.time", "DateTime")

internal fun DateTimeColumnType.typeNameOf(function: TemporalFunction): ClassName = when (this) {
    DateTimeColumnType.KOTLIN_DATETIME -> when (function) {
        TemporalFunction.DATE -> ClassName("kotlinx.datetime", "LocalDate")
        TemporalFunction.TIME -> ClassName("kotlinx.datetime", "LocalTime")
        TemporalFunction.DATETIME -> ClassName("kotlinx.datetime", "LocalDateTime")
        TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> JAVA_OFFSET_DATE_TIME
    }
    DateTimeColumnType.JAVA_TIME -> when (function) {
        TemporalFunction.DATE -> ClassName("java.time", "LocalDate")
        TemporalFunction.TIME -> ClassName("java.time", "LocalTime")
        TemporalFunction.DATETIME -> ClassName("java.time", "LocalDateTime")
        TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> JAVA_OFFSET_DATE_TIME
    }
    DateTimeColumnType.JODA_TIME -> when (function) {
        TemporalFunction.TIME -> ClassName("org.joda.time", "LocalTime")
        TemporalFunction.DATE, TemporalFunction.DATETIME, TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> JODA_DATE_TIME
    }
}

private fun DateTimeColumnType.columnTypeNameOf(function: TemporalFunction): String = when (this) {
    DateTimeColumnType.KOTLIN_DATETIME -> when (function) {
        TemporalFunction.DATE -> "KotlinLocalDateColumnType"
        TemporalFunction.TIME -> "KotlinLocalTimeColumnType"
        TemporalFunction.DATETIME -> "KotlinLocalDateTimeColumnType"
        TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> "KotlinOffsetDateTimeColumnType"
    }
    DateTimeColumnType.JAVA_TIME -> when (function) {
        TemporalFunction.DATE -> "JavaLocalDateColumnType"
        TemporalFunction.TIME -> "JavaLocalTimeColumnType"
        TemporalFunction.DATETIME -> "JavaLocalDateTimeColumnType"
        TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> "JavaOffsetDateTimeColumnType"
    }
    DateTimeColumnType.JODA_TIME -> when (function) {
        TemporalFunction.DATE -> "JodaLocalDateColumnType"
        TemporalFunction.TIME -> "JodaLocalTimeColumnType"
        TemporalFunction.DATETIME -> "JodaLocalDateTimeColumnType"
        TemporalFunction.TIMESTAMP_WITH_TIME_ZONE -> "DateTimeWithTimeZoneColumnType"
    }
}

internal fun DateTimeColumnType.toColumnDefinition(function: TemporalFunction): ColumnDefinition = ColumnDefinition(
    function.function,
    typeNameOf(function),
    functionPackage = functionPackage,
    requiredModule = module,
    columnTypeExpression = columnType(columnTypeNameOf(function), packageName = functionPackage),
)
