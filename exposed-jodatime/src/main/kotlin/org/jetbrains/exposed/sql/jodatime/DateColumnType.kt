package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.sql.ResultSet
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT)
private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS")
private val SQLITE_DATE_STRING_FORMATTER = ISODateTimeFormat.yearMonthDay()

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(date.substringAfterLast('.', "").length)
private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "YYYY-MM-dd HH:mm:ss"
    val newFormat = baseFormat + if (fraction in 1..9) ".${"S".repeat(fraction)}" else ""
    return DateTimeFormat.forPattern(newFormat)
}

class DateColumnType(val time: Boolean) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = time
    override fun sqlType(): String = if (time) currentDialect.dataTypeProvider.dateTimeType() else "DATE"

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val dateTime = when (value) {
            is DateTime -> value
            is java.sql.Date -> DateTime(value.time)
            is java.sql.Timestamp -> DateTime(value.time)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return if (time) {
            "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(dateTime.toDateTime(DateTimeZone.getDefault()))}'"
        } else {
            "'${DEFAULT_DATE_STRING_FORMATTER.print(dateTime)}'"
        }
    }

    override fun valueFromDB(value: Any): Any {
        val dateTime = when (value) {
            is DateTime -> value
            is java.sql.Date -> DateTime(value.time)
            is java.sql.Timestamp -> DateTime(value.time)
            is Int -> DateTime(value.toLong())
            is Long -> DateTime(value)
            is String -> when {
                time -> DateTime.parse(value, formatterForDateTimeString(value))
                currentDialect is SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parseDateTime(value)
                else -> DEFAULT_DATE_STRING_FORMATTER.parseDateTime(value)
            }

            else -> {
                if (localDateTimeClass == value.javaClass) {
                    DateTime.parse(value.toString())
                } else {
                    valueFromDB(value.toString()) as DateTime
                }
            }
        }
        return when (time) {
            true -> dateTime
            false -> dateTime.withTimeAtStartOfDay()
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        /*
         Since MySQL ConnectorJ 8.0.23 driver returns LocalDateTime instead of String for DateTime columns.
         As exposed-jodatime module should work with Java 1.7 it's necessary to fetch it as String as it was before.

         MariaDB however may return '0000-00-00 00:00:00' on getString even though it is also null in many other
          regards (and can obviously never be converted to anything reasonable). So dont do that for MariaDB.
         */
        return if (time && localDateTimeClass != null && currentDialect is MysqlDialect) {
            rs.getObject(index, localDateTimeClass)
        } else if (time && currentDialect is MysqlDialect && currentDialect !is MariaDBDialect) {
            rs.getObject(index, String::class.java)
        } else super.readObject(rs, index)
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is DateTime && time && currentDialect is SQLiteDialect -> SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.print(value)
        value is DateTime && time -> java.sql.Timestamp(value.millis)
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

    companion object {
        // https://www.baeldung.com/java-check-class-exists
        private val localDateTimeClass = try {
            Class.forName("java.time.LocalDateTime", false, this::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        }
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
