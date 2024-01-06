package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
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

private val SQLITE_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSZZ").withLocale(Locale.ROOT)
}

private val MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSSZZ").withLocale(Locale.ROOT)
}

private val ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS ZZ").withLocale(Locale.ROOT)
}

private val DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    ISODateTimeFormat.dateTime().withLocale(Locale.ROOT)
}

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)
private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "YYYY-MM-dd HH:mm:ss"
    val newFormat = baseFormat + if (fraction in 1..9) ".${"S".repeat(fraction)}" else ""
    return DateTimeFormat.forPattern(newFormat)
}

/**
 * Column for storing dates, as [DateTime]. If [time] is set to `true`, both date and time data is stored.
 *
 * @sample org.jetbrains.exposed.sql.jodatime.datetime
 */
class DateColumnType(val time: Boolean) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = time
    override fun sqlType(): String = if (time) {
        currentDialect.dataTypeProvider.dateTimeType()
    } else {
        currentDialect.dataTypeProvider.dateType()
    }

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
            is java.time.LocalDateTime -> DateTime.parse(value.toString())
            is java.time.OffsetDateTime -> valueFromDB(value.toLocalDateTime()) as DateTime
            else -> valueFromDB(value.toString()) as DateTime
        }
        return when (time) {
            true -> dateTime
            false -> dateTime.withTimeAtStartOfDay()
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // Since MySQL ConnectorJ 8.0.23, driver returns LocalDateTime instead of String for DateTime columns.
        return when {
            time && currentDialect is MysqlDialect -> {
                rs.getObject(index, java.time.LocalDateTime::class.java)
            }
            time && currentDialect is OracleDialect -> rs.getObject(index, java.sql.Timestamp::class.java)
            else -> super.readObject(rs, index)
        }
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is DateTime && time && currentDialect is SQLiteDialect -> SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.print(value)
        value is DateTime && time -> java.sql.Timestamp(value.millis)
        value is DateTime && currentDialect is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.print(value)
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

/**
 * Column for storing dates and times with time zone, as [DateTime].
 *
 * @sample org.jetbrains.exposed.sql.jodatime.timestampWithTimeZone
 */
class DateTimeWithTimeZoneColumnType : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType()

    override fun nonNullValueToString(value: Any): String = when (value) {
        is DateTime -> {
            when (currentDialect) {
                is SQLiteDialect -> "'${SQLITE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
                is MysqlDialect -> "'${MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
                is OracleDialect -> "'${ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
                else -> "'${DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
            }
        }
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun valueFromDB(value: Any): DateTime = when (value) {
        is java.time.OffsetDateTime -> DateTime.parse(value.toString())
        is String -> {
            if (currentDialect is SQLiteDialect) {
                DateTime.parse(value, SQLITE_DATE_TIME_WITH_TIME_ZONE_FORMATTER)
            } else {
                DateTime.parse(value)
            }
        }
        is java.sql.Timestamp -> DateTime(value.time)
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, java.sql.Timestamp::class.java)
        else -> rs.getObject(index, java.time.OffsetDateTime::class.java)
    }

    override fun notNullValueToDB(value: Any): Any = when (value) {
        is DateTime -> {
            when (currentDialect) {
                is SQLiteDialect -> SQLITE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
                is MysqlDialect -> MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
                else -> java.sql.Timestamp(value.millis)
            }
        }
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
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
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType())
