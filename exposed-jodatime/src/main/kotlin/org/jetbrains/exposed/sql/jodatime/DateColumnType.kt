@file: Suppress("MagicNumber", "NestedBlockDepth")

package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToLong

private val DEFAULT_DATE_STRING_FORMATTER by lazy {
    DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private val MYSQL_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault()) }

private val SQLITE_DATE_STRING_FORMATTER by lazy { ISODateTimeFormat.yearMonthDay() }

// Example result: 2023-07-07 14:42:29.343+02:00 or 2023-07-07 12:42:29.343Z
private val SQLITE_OFFSET_DATE_TIME_FORMATTER by lazy {
    java.time.format.DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalEnd()
        .optionalStart()
        .appendPattern("XXX")
        .optionalEnd()
        .toFormatter()
        .withLocale(Locale.ROOT)
}

private val MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSZZ").withLocale(Locale.ROOT)
}

private val ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS ZZ").withLocale(Locale.ROOT)
}

private val DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER by lazy {
    ISODateTimeFormat.dateTime().withLocale(Locale.ROOT)
}

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormat.forPattern("1970-01-01 HH:mm:ss.SSS").withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private val DEFAULT_TIME_STRING_FORMATTER by lazy {
    ISODateTimeFormat.time().withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)

private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "YYYY-MM-dd HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormat.forPattern(newFormat).withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private fun oracleDateTimeLiteral(dateTime: DateTime) =
    "TO_TIMESTAMP('${DEFAULT_DATE_TIME_STRING_FORMATTER.print(dateTime)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleDateTimeWithTimezoneLiteral(dateTime: DateTime) =
    "TO_TIMESTAMP_TZ('${ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(dateTime)}', 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')"

private fun oracleDateLiteral(dateTime: DateTime) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.print(dateTime)}', 'YYYY-MM-DD')"

private fun postgresDateTimeAsDefaultFormatter(dateTime: DateTime, precision: Byte?): DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .apply {
            if (dateTime.millisOfSecond != 0) {
                when (precision) {
                    null -> { // PostgreSQL default precision is 6, but JodaTime max precision is 3
                        this.appendLiteral('.')
                        this.appendFractionOfSecond(1, 3)
                    }
                    else -> {
                        if (precision > 0) {
                            this.appendLiteral('.')
                            this.appendFractionOfSecond(1, (precision.takeIf { it <= 3 } ?: 3).toInt())
                        }
                    }
                }
            }
        }
        .toFormatter()
        .withLocale(Locale.ROOT)

private fun mysqlDateTimeAsDefaultFormatter(isFractionDateTimeSupported: Boolean, precision: Byte?) =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .apply {
            if (isFractionDateTimeSupported && precision != null && precision > 0) {
                this.appendLiteral('.')
                val digits = (precision.takeIf { it <= 3 } ?: 3).toInt()
                this.appendFractionOfSecond(digits, digits)
            }
        }
        .toFormatter()
        .withLocale(Locale.ROOT)

private fun roundedDateTime(dateTime: DateTime, precision: Byte?) =
    if (precision != null && precision > 0) {
        val factor = (10f).pow(3 - precision.toInt())
        val roundedMillis = (dateTime.millisOfSecond / factor).roundToLong() * factor.toLong()
        dateTime.withMillis(dateTime.millis / 1000 * 1000 + roundedMillis)
    } else {
        val roundedMillis = if (dateTime.millisOfSecond >= 500) 1000 else 0
        dateTime.withMillis(dateTime.millis / 1000 * 1000 + roundedMillis) // Round to the nearest second
    }

/**
 * Column for storing dates, as [DateTime]. If [time] is set to `true`, both date and time data is stored.
 *
 * @sample org.jetbrains.exposed.sql.jodatime.datetime
 */
class DateColumnType(
    val time: Boolean,
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<DateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = time

    override fun sqlType(): String = if (time) {
        currentDialect.dataTypeProvider.dateTimeType(precision)
    } else {
        currentDialect.dataTypeProvider.dateType()
    }

    override fun nonNullValueToString(value: DateTime): String {
        return if (time) {
            when {
                currentDialect is OracleDialect -> oracleDateTimeLiteral(value)
                (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
                    "'${MYSQL_DATE_TIME_STRING_FORMATTER.print(value)}'"
                else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value)}'"
            }
        } else {
            if (currentDialect is OracleDialect) {
                return oracleDateLiteral(value)
            }
            return "'${DEFAULT_DATE_STRING_FORMATTER.print(value)}'"
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
            is OffsetDateTime -> valueFromDB(value.toLocalDateTime()) as DateTime
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
            time && dialect is SQLiteDialect -> DEFAULT_DATE_TIME_STRING_FORMATTER.print(value)
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
                    val formatter = postgresDateTimeAsDefaultFormatter(value, precision)
                        .withZone(DateTimeZone.getDefault())
                    "'${formatter.print(value)}'::timestamp without time zone"
                } else {
                    "'${DEFAULT_DATE_STRING_FORMATTER.print(value)}'::date"
                }
            }
            time && dialect is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                    .withZone(DateTimeZone.getDefault())

                if (dialect is MariaDBDialect) {
                    "'${formatter.print(value)}'"
                } else {
                    val roundedValue = roundedDateTime(value, precision)
                    "'${formatter.print(roundedValue)}'"
                }
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
 * Column for storing times, as [LocalTime].
 *
 * @sample org.jetbrains.exposed.sql.jodatime.time
 */
class LocalTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType(precision)

    override fun nonNullValueToString(value: LocalTime): String =
        when (currentDialect) {
            is OracleDialect -> "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.print(value)}'"
            else -> "'${DEFAULT_TIME_STRING_FORMATTER.print(value)}'"
        }

    override fun valueFromDB(value: Any): LocalTime? = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().let {
            LocalTime.fromMillisOfDay(it.toNanoOfDay() / 1_000_000) // Convert nanos to millis
        }
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime().let {
            LocalTime.fromMillisOfDay(it.toNanoOfDay() / 1_000_000) // Convert nanos to millis
        }
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect) {
                formatterForDateTimeString(value)
            } else {
                ISODateTimeFormat.timeParser().withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
            }
            LocalTime.parse(value, formatter)
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalTime): Any =
        when (val dialect = currentDialect) {
            is SQLiteDialect, is SQLServerDialect, is H2Dialect -> DEFAULT_TIME_STRING_FORMATTER.print(value)
            is MysqlDialect -> {
                if (dialect.isFractionDateTimeSupported()) {
                    DEFAULT_TIME_STRING_FORMATTER.print(value)
                } else {
                    java.sql.Time.valueOf(java.time.LocalTime.ofNanoOfDay(value.millisOfDay * 1_000_000L))
                }
            }
            else -> java.time.LocalTime.ofNanoOfDay(value.millisOfDay * 1_000_000L)
        }

    override fun nonNullValueAsDefaultString(value: LocalTime): String = when (val dialect = currentDialect) {
        is PostgreSQLDialect -> {
            val formatter = DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .apply {
                    if (value.millisOfSecond != 0) {
                        when (precision) {
                            null -> { // PostgreSQL default precision is 6, but JodaTime max precision is 3
                                this.appendLiteral('.')
                                this.appendFractionOfSecond(1, 3)
                            }
                            else -> {
                                if (precision > 0) {
                                    this.appendLiteral('.')
                                    this.appendFractionOfSecond(1, (precision.takeIf { it <= 3 } ?: 3).toInt())
                                }
                            }
                        }
                    }
                }
                .toFormatter()
                .withLocale(Locale.ROOT)
                .withZone(DateTimeZone.getDefault())
            "'${formatter.print(value)}'::time without time zone"
        }
        is MysqlDialect -> {
            val formatter = DateTimeFormatterBuilder()
                .appendPattern("HH:mm:ss")
                .apply {
                    if (dialect.isFractionDateTimeSupported() && precision != null && precision > 0) {
                        this.appendLiteral('.')
                        val digits = (precision.takeIf { it <= 3 } ?: 3).toInt()
                        this.appendFractionOfSecond(digits, digits)
                    }
                }
                .toFormatter()
                .withLocale(Locale.ROOT)
                .withZone(DateTimeZone.getDefault())

            if (dialect is MariaDBDialect) {
                "'${formatter.print(value)}'"
            } else {
                val roundedValue = roundedLocalTime(value, precision)
                "'${formatter.print(roundedValue)}'"
            }
        }
        else -> super.nonNullValueAsDefaultString(value)
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (val dialect = currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is MysqlDialect -> if (dialect.isFractionDateTimeSupported()) {
            rs.getTimestamp(index)
        } else {
            super.readObject(rs, index)
        }
        else -> rs.getString(index)
    }

    private fun longToLocalTime(millis: Long): LocalTime =
        Instant.ofEpochMilli(millis).toDateTime(DateTimeZone.getDefault()).toLocalTime()

    private fun roundedLocalTime(localTime: LocalTime, precision: Byte?) =
        if (precision != null && precision > 0) {
            val factor = (10f).pow(3 - precision.toInt())
            val roundedMillis = (localTime.millisOfSecond / factor).roundToLong() * factor.toLong()
            localTime.withMillisOfSecond(roundedMillis.toInt())
        } else {
            val roundedMillis = if (localTime.millisOfSecond >= 500) 1000 else 0
            localTime.withMillisOfSecond(0).plusSeconds(if (roundedMillis == 1000) 1 else 0) // Round to the nearest second
        }
}

/**
 * Column for storing dates and times with time zone, as [DateTime].
 *
 * @sample org.jetbrains.exposed.sql.jodatime.timestampWithTimeZone
 */
class DateTimeWithTimeZoneColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<DateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType(precision)

    override fun nonNullValueToString(value: DateTime): String = when (currentDialect) {
        is SQLiteDialect -> {
            val instant = java.time.Instant.ofEpochMilli(value.millis)
            val offsetDateTime = OffsetDateTime.ofInstant(instant, value.zone.toTimeZone().toZoneId())
            "'${offsetDateTime.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        }
        is MysqlDialect -> "'${MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
        is OracleDialect -> oracleDateTimeWithTimezoneLiteral(value)
        else -> "'${DEFAULT_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
    }

    override fun valueFromDB(value: Any): DateTime = when (value) {
        is DateTime -> value
        is OffsetDateTime -> DateTime.parse(value.toString())
        is ZonedDateTime -> DateTime.parse(value.toOffsetDateTime().toString())
        is String -> {
            if (currentDialect is SQLiteDialect) {
                val temporalAccessor = SQLITE_OFFSET_DATE_TIME_FORMATTER.parse(value)
                val offsetDateTime = if (temporalAccessor.isSupported(ChronoField.OFFSET_SECONDS)) {
                    OffsetDateTime.from(temporalAccessor)
                } else {
                    OffsetDateTime.from(java.time.LocalDateTime.from(temporalAccessor).atOffset(UTC))
                }
                val dateTimeZone: DateTimeZone = DateTimeZone.forID(offsetDateTime.toZonedDateTime().offset.id)
                DateTime(offsetDateTime.toInstant().toEpochMilli(), dateTimeZone)
            } else {
                DateTime.parse(value)
            }
        }
        is java.sql.Timestamp -> DateTime(value.time)
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, ZonedDateTime::class.java)
        else -> rs.getObject(index, OffsetDateTime::class.java)
    }

    override fun notNullValueToDB(value: DateTime): Any = when (currentDialect) {
        is SQLiteDialect -> {
            val instant = java.time.Instant.ofEpochMilli(value.millis)
            val offsetDateTime = OffsetDateTime.ofInstant(instant, value.zone.toTimeZone().toZoneId())
            offsetDateTime.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)
        }
        is MysqlDialect -> MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
        else -> {
            val instant = java.time.Instant.ofEpochMilli(value.millis)
            OffsetDateTime.ofInstant(instant, value.zone.toTimeZone().toZoneId())
        }
    }

    override fun nonNullValueAsDefaultString(value: DateTime): String {
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect -> { // +00 appended because PostgreSQL stores it in UTC time zone
                val formatter = postgresDateTimeAsDefaultFormatter(value, precision)
                "'${formatter.print(value)}+00'::timestamp with time zone"
            }
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                val roundedValue = roundedDateTime(value, precision)
                "'${formatter.print(roundedValue)}'"
            }
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
 * @param precision The fractional seconds precision
 */
fun Table.datetime(name: String, precision: Byte? = null): Column<DateTime> = registerColumn(name, DateColumnType(true, precision))

/**
 * A time column to store a time.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.time(name: String, precision: Byte? = null): Column<LocalTime> = registerColumn(name, LocalTimeColumnType(precision))

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.timestampWithTimeZone(name: String, precision: Byte? = null): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType(precision))
