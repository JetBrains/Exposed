package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.sql.ResultSet
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT) }
private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT) }
private val MYSQL_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withLocale(Locale.ROOT) }
private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS") }
private val SQLITE_DATE_STRING_FORMATTER by lazy { ISODateTimeFormat.yearMonthDay() }

private val SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSZZ").withLocale(Locale.ROOT)
}

private val ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS ZZ").withLocale(Locale.ROOT)
}

private val DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    ISODateTimeFormat.dateTime().withLocale(Locale.ROOT)
}

private val MYSQL_FRACTION_DATE_TIME_AS_DEFAULT_FORMATTER by lazy {
    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
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
class DateColumnType(val time: Boolean) : ColumnType<DateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = time
    override fun sqlType(): String = if (time) {
        currentDialect.dataTypeProvider.dateTimeType()
    } else {
        currentDialect.dataTypeProvider.dateType()
    }

    override fun nonNullValueToString(value: DateTime): String {
        return if (time) {
            when {
                (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
                    "'${MYSQL_DATE_TIME_STRING_FORMATTER.print(value.toDateTime(DateTimeZone.getDefault()))}'"
                else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value.toDateTime(DateTimeZone.getDefault()))}'"
            }
        } else {
            "'${DEFAULT_DATE_STRING_FORMATTER.print(value)}'"
        }
    }

    override fun valueFromDB(value: Any): DateTime? {
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
        val dialect = currentDialect
        return when {
            time && dialect is MysqlDialect -> {
                rs.getObject(index, java.time.LocalDateTime::class.java)
            }
            time && dialect is OracleDialect -> rs.getObject(index, java.sql.Timestamp::class.java)
            else -> super.readObject(rs, index)
        }
    }

    override fun notNullValueToDB(value: DateTime): Any {
        val dialect = currentDialect
        return when {
            time && dialect is SQLiteDialect -> SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.print(value)
            time -> java.sql.Timestamp(value.millis)
            dialect is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.print(value)
            else -> java.sql.Date(value.millis)
        }
    }

    override fun nonNullValueAsDefaultString(value: DateTime): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect -> {
                if (time) {
                    "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value).trimEnd('0').trimEnd('.')}'::timestamp without time zone"
                } else {
                    "'${DEFAULT_DATE_STRING_FORMATTER.print(value)}'::date"
                }
            }
            time && (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value).trimEnd('0').trimEnd('.')}'"
            time && dialect is MysqlDialect && dialect.isFractionDateTimeSupported() -> {
                "'${MYSQL_FRACTION_DATE_TIME_AS_DEFAULT_FORMATTER.print(value)}'"
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
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
class DateTimeWithTimeZoneColumnType : ColumnType<DateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType()

    override fun nonNullValueToString(value: DateTime): String = when (currentDialect) {
        is SQLiteDialect -> "'${SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
        is MysqlDialect -> "'${SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
        is OracleDialect -> "'${ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
        else -> "'${DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
    }

    override fun valueFromDB(value: Any): DateTime = when (value) {
        is java.time.OffsetDateTime -> DateTime.parse(value.toString())
        is String -> {
            if (currentDialect is SQLiteDialect) {
                DateTime.parse(value, SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER)
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

    override fun notNullValueToDB(value: DateTime): Any = when (currentDialect) {
        is SQLiteDialect -> SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
        is MysqlDialect -> SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
        else -> java.sql.Timestamp(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: DateTime): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value).trimEnd('0')}+00'::timestamp with time zone"
            (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value).trimEnd('0')}'"
            dialect is MysqlDialect -> "'${MYSQL_FRACTION_DATE_TIME_AS_DEFAULT_FORMATTER.print(value)}'"
            else -> super.nonNullValueAsDefaultString(value)
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

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType())
