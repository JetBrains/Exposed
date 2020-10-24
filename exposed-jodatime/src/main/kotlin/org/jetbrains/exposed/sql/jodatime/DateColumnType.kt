package org.jetbrains.exposed.sql.jodatime

import org.h2.api.TimestampWithTimeZone
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT)
private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS")
private val SQLITE_DATE_STRING_FORMATTER = ISODateTimeFormat.yearMonthDay()
private val DEFAULT_DATE_TIME_WITH_TIMEZONE_FORMATTER = ISODateTimeFormat.dateTime()


private val DATE_TIME_SPACE_SEPARATED_WITH_TIMEZONE_STRING_FORMATTER = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendOptional( DateTimeFormatterBuilder()
                .appendLiteral('.')
                .appendFractionOfSecond(3, 9)
                .toParser())
        .appendTimeZoneOffset(null, "+00", true, 2, 2)
        .toFormatter()

private val DATE_TIME_SPACE_SEPARATED_WITHOUT_TIMEZONE_STRING_FORMATTER = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendOptional( DateTimeFormatterBuilder()
                .appendLiteral('.')
                .appendFractionOfSecond(3, 9)
                .toParser())
        .toFormatter()

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(date.substringAfterLast('.', "").length)
private fun dateTimeWithFractionFormat(fraction: Int) : DateTimeFormatter {
    val baseFormat = "YYYY-MM-dd HH:mm:ss"
    val newFormat = if(fraction in 1..9)
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    else
        baseFormat
    return DateTimeFormat.forPattern(newFormat)
}

class DateColumnType(val time: Boolean): ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = time
    override fun sqlType(): String  = if (time) currentDialect.dataTypeProvider.dateTimeType() else "DATE"

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val dateTime: DateTime = when (value) {
            is DateTime -> value
            is java.sql.Date -> DateTime(value.time)
            is java.sql.Timestamp -> DateTime(value.time)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return if (time)
            "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(dateTime.toDateTime(DateTimeZone.getDefault()))}'"
        else
            "'${DEFAULT_DATE_STRING_FORMATTER.print(dateTime)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is DateTime -> value
        is java.sql.Date ->  DateTime(value.time)
        is java.sql.Timestamp -> DateTime(value.time)
        is Int -> DateTime(value.toLong())
        is Long -> DateTime(value)
        is String -> when {
            currentDialect is SQLiteDialect && time -> DateTime.parse(value, formatterForDateTimeString(value))
            currentDialect is SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parseDateTime(value)
            else -> value
        }
        // REVIEW
        else -> DEFAULT_DATE_TIME_STRING_FORMATTER.parseDateTime(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is DateTime && time && currentDialect is SQLiteDialect -> SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.print(value)
        value is DateTime && time  -> java.sql.Timestamp(value.millis)
        value is DateTime -> java.sql.Date(value.millis)
        else -> value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateColumnType) return false
        if (!super.equals(other)) return false

        if (time != other.time) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + time.hashCode()
        return result
    }
}

class DateTimeWithTimeZoneColumnType: ColumnType(), IDateColumnType {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.dateTimeTzType()

    override fun nonNullValueToString(value: Any): String {
        val dateTime = when (value) {
            is DateTime -> value
            is java.sql.Date ->  DateTime(value.time)
            is java.sql.Timestamp -> DateTime(value.time)
            is Int -> DateTime(value.toLong())
            is Long -> DateTime(value)
            is String -> DateTime.parse(value, DEFAULT_DATE_TIME_WITH_TIMEZONE_FORMATTER)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }
        return "'${DEFAULT_DATE_TIME_WITH_TIMEZONE_FORMATTER.print(dateTime)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is DateTime -> value
        is TimestampWithTimeZone -> DateTime.parse(value.toString(), DATE_TIME_SPACE_SEPARATED_WITH_TIMEZONE_STRING_FORMATTER)
        is java.sql.Date ->  DateTime(value.time)
        is java.sql.Timestamp -> DateTime(value.time)
        is Int -> DateTime(value.toLong())
        is Long -> DateTime(value)
        is String ->
            value.toLongOrNull()?.let { DateTime(it) }
                    ?: when(currentDialect) {
                        is MysqlDialect, is SQLiteDialect -> DATE_TIME_SPACE_SEPARATED_WITHOUT_TIMEZONE_STRING_FORMATTER.parseDateTime(value) // MySQL doesn't actually support this type.
                        else -> DATE_TIME_SPACE_SEPARATED_WITH_TIMEZONE_STRING_FORMATTER.parseDateTime(value)
                    }
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is DateTime -> java.sql.Timestamp(value.millis)
        else -> value
    }

    override val hasTimePart: Boolean
        get() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateTimeWithTimeZoneColumnType) return false
        if (!super.equals(other)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result *= 31
        return result
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<DateTime> = registerColumn(name, DateColumnType(false))

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<DateTime> = registerColumn(name, DateColumnType(true))


/**
 * A datetime column to store a date, a time and a timezone.
 *
 * @param name The column name
 */
fun Table.datetimetz(name: String): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType())

