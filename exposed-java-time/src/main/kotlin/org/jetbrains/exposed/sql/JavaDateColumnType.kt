package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER by lazy { DateTimeFormatter.ofPattern("YYYY-MM-d").withLocale(Locale.ROOT).withZone(ZoneId.systemDefault()) }
private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-d HH:mm:ss.SSSSSS").withLocale(Locale.ROOT).withZone(ZoneId.systemDefault()) }
private val SQLITE_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormatter.ofPattern("yyyy-MM-d HH:mm:ss").withZone(ZoneId.systemDefault()) }

private val LocalDate.millis get() = atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

class JavaLocalDateColumnType : ColumnType(), IDateColumnType {
    override fun sqlType(): String = "DATE"

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDate -> Instant.from(value.atStartOfDay(ZoneId.systemDefault()))
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
        is Int -> Instant.ofEpochMilli(value.toLong()).atZone(ZoneId.systemDefault()).toLocalDate() //LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong()), ZoneId.systemDefault()).toLocalDate()
        is Long -> Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate() //LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()).toLocalDate()
        is String -> when (currentDialect) {
            is SQLiteDialect -> LocalDate.parse(value)
            else -> value
        }
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: Any) = when {
        value is LocalDate -> java.sql.Date(value.millis)
        else -> value
    }

    companion object {
        internal val INSTANCE = JavaLocalDateColumnType()
    }
}

class JavaLocalDateTimeColumnType : ColumnType(), IDateColumnType {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDateTime -> Instant.from(value.atZone(ZoneId.systemDefault()))
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
            is SQLiteDialect -> LocalDateTime.parse(value, SQLITE_DATE_TIME_STRING_FORMATTER)
            else -> value
        }
        else -> LocalDateTime.parse(value.toString(), DEFAULT_DATE_TIME_STRING_FORMATTER)
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is LocalDateTime) {
            return java.sql.Timestamp(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        return value
    }

    companion object {
        internal val INSTANCE = JavaLocalDateTimeColumnType()
    }
}


/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, JavaLocalDateColumnType())

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<LocalDateTime> = registerColumn(name, JavaLocalDateTimeColumnType())
