package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.RowApi
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.util.*

private val DEFAULT_DATE_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT) }
private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT) }
private val MYSQL_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss").withLocale(Locale.ROOT) }
private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER by lazy { DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS") }
private val SQLITE_DATE_STRING_FORMATTER by lazy { ISODateTimeFormat.yearMonthDay() }

// Example result: 2023-07-07 14:42:29.343+02:00 or 2023-07-07 12:42:29.343Z
private val SQLITE_OFFSET_DATE_TIME_FORMATTER by lazy {
    java.time.format.DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendPattern(".SSS")
        .optionalEnd()
        .optionalStart()
        .appendPattern("XXX")
        .optionalEnd()
        .toFormatter()
        .withLocale(Locale.ROOT)
}

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

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormat.forPattern("1970-01-01 HH:mm:ss").withLocale(Locale.ROOT).withZone(DateTimeZone.UTC)
}

private val DEFAULT_TIME_STRING_FORMATTER by lazy {
    DateTimeFormat.forPattern("HH:mm:ss").withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
}

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)

private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "YYYY-MM-dd HH:mm:ss"
    val newFormat = baseFormat + if (fraction in 1..9) ".${"S".repeat(fraction)}" else ""
    return DateTimeFormat.forPattern(newFormat)
}

private fun oracleDateTimeLiteral(dateTime: DateTime) =
    "TO_TIMESTAMP('${DEFAULT_DATE_TIME_STRING_FORMATTER.print(dateTime)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleDateTimeWithTimezoneLiteral(dateTime: DateTime) =
    "TO_TIMESTAMP_TZ('${ORACLE_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(dateTime)}', 'YYYY-MM-DD HH24:MI:SS.FF3 TZH:TZM')"

private fun oracleDateLiteral(dateTime: DateTime) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.print(dateTime)}', 'YYYY-MM-DD')"

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
                currentDialect is OracleDialect -> oracleDateTimeLiteral(value.toDateTime(DateTimeZone.getDefault()))
                (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
                    "'${MYSQL_DATE_TIME_STRING_FORMATTER.print(value.toDateTime(DateTimeZone.getDefault()))}'"
                else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value.toDateTime(DateTimeZone.getDefault()))}'"
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

    override fun readObject(rs: RowApi, index: Int): Any? {
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
 * Column for storing times, as [LocalTime].
 *
 * @sample org.jetbrains.exposed.sql.jodatime.time
 */
class LocalTimeColumnType : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: LocalTime): String {
        val dialect = currentDialect
        if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            return "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.print(value)}'"
        }
        return "'${DEFAULT_TIME_STRING_FORMATTER.print(value)}'"
    }

    override fun valueFromDB(value: Any): LocalTime? = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().let { LocalTime(it.hour, it.minute, it.second) }
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime().let { LocalTime(it.hour, it.minute, it.second) }
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                formatterForDateTimeString(value)
            } else {
                ISODateTimeFormat.timeParser().withLocale(Locale.ROOT).withZone(DateTimeZone.getDefault())
            }
            LocalTime.parse(value, formatter)
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalTime): Any = when {
        currentDialect is SQLiteDialect -> DEFAULT_TIME_STRING_FORMATTER.print(value)
        currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> ORACLE_TIME_STRING_FORMATTER.print(value)
        else -> java.sql.Time.valueOf(DEFAULT_TIME_STRING_FORMATTER.print(value))
    }

    override fun nonNullValueAsDefaultString(value: LocalTime): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::time without time zone"
        is MysqlDialect -> "'${DEFAULT_TIME_STRING_FORMATTER.print(value)}'"
        else -> super.nonNullValueAsDefaultString(value)
    }

    private fun longToLocalTime(millis: Long): LocalTime =
        Instant.ofEpochMilli(millis).toDateTime(DateTimeZone.getDefault()).toLocalTime()
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
        is SQLiteDialect -> {
            val instant = java.time.Instant.ofEpochMilli(value.millis)
            val offsetDateTime = OffsetDateTime.ofInstant(instant, value.zone.toTimeZone().toZoneId())
            "'${offsetDateTime.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        }
        is MysqlDialect -> "'${SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)}'"
        is OracleDialect -> oracleDateTimeWithTimezoneLiteral(value.toDateTime(DateTimeZone.getDefault()))
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

    override fun readObject(rs: RowApi, index: Int): Any? = when (currentDialect) {
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
        is MysqlDialect -> SQLITE_AND_MYSQL_DATE_TIME_WITH_TIME_ZONE_FORMATTER.print(value)
        else -> java.sql.Timestamp(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: DateTime): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect -> // +00 appended because PostgreSQL stores it in UTC time zone
                "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value).trimEnd('0').trimEnd('.')}+00'::timestamp with time zone"
            (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(value)}'"
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
 * A time column to store a time.
 *
 * @param name The column name
 */
fun Table.time(name: String): Column<LocalTime> = registerColumn(name, LocalTimeColumnType())

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType())
