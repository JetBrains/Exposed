package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT)
private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
private val SQLITE_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss")
private val SQLITE_DATE_STRING_FORMATTER = ISODateTimeFormat.yearMonthDay()

class DateColumnType(val time: Boolean): ColumnType(), IDateColumnType {
    override fun sqlType(): String  = if (time) currentDialect.dataTypeProvider.dateTimeType() else "DATE"

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val dateTime = when (value) {
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
            currentDialect is SQLiteDialect && time -> SQLITE_DATE_TIME_STRING_FORMATTER.parseDateTime(value)
            currentDialect is SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parseDateTime(value)
            else -> value
        }
        // REVIEW
        else -> DEFAULT_DATE_TIME_STRING_FORMATTER.parseDateTime(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is DateTime) {
            val millis = value.millis
            if (time) {
                return java.sql.Timestamp(millis)
            } else {
                return java.sql.Date(millis)
            }
        }
        return value
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
