package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-dd").withLocale(Locale.ROOT)
private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
private val SQLITE_DATE_TIME_STRING_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")
private val SQLITE_DATE_STRING_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

private val LocalDate.millis get() = atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

class LocalDateColumnType : ColumnType(), IDateColumnType {
    override fun sqlType(): String = "DATE"

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDate -> Instant.from(value)
            is java.sql.Date -> value.toInstant()
            is java.sql.Timestamp -> value.toInstant()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'${DEFAULT_DATE_STRING_FORMATTER.format(instant)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalDate()
        is Int -> LocalDate.from(Instant.ofEpochMilli(value.toLong()))
        is Long -> LocalDate.from(Instant.ofEpochMilli(value.toLong()))
        is String -> when (currentDialect) {
            is SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parse(value)
            else -> value
        }
        else -> DEFAULT_DATE_STRING_FORMATTER.parse(value.toString())
    }

    override fun notNullValueToDB(value: Any) = when {
        value is LocalDate -> java.sql.Date(value.millis)
        else -> value
    }

    companion object {
        internal val INSTANSE = LocalDateColumnType()
    }
}

class LocalDateTimeColumnType : ColumnType(), IDateColumnType {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDateTime -> Instant.from(value)
            is java.sql.Date -> Instant.ofEpochMilli(value.time)
            is java.sql.Timestamp -> Instant.ofEpochMilli(value.time)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is LocalDateTime -> value
        is java.sql.Date -> value.toLocalDate().atStartOfDay()
        is java.sql.Timestamp -> value.toLocalDateTime()
        is Int -> LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong()), ZoneId.systemDefault())
        is Long -> LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault())
        is String -> when (currentDialect) {
            is SQLiteDialect -> SQLITE_DATE_TIME_STRING_FORMATTER.parse(value)
            else -> value
        }
        else -> DEFAULT_DATE_TIME_STRING_FORMATTER.parse(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is LocalDateTime) {
            return java.sql.Timestamp(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        return value
    }

    companion object {
        internal val INSTANSE = LocalDateTimeColumnType()
    }
}


/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, LocalDateColumnType())

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<LocalDateTime> = registerColumn(name, LocalDateTimeColumnType())
