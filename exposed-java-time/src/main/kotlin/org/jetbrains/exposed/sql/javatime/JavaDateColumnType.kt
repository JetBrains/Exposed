@file: Suppress("MagicNumber")

package org.jetbrains.exposed.sql.javatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.sql.ResultSet
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToLong

private val DEFAULT_DATE_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(UTC)
}

private val DEFAULT_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(Locale.ROOT)
}

private val DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter(Locale.ROOT)
        .withZone(ZoneId.systemDefault())
}

private val MYSQL_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
}

private val TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter(Locale.ROOT)
        .withZone(UTC)
}

private val MYSQL_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).withZone(UTC)
}

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendLiteral("1970-01-01 ")
        .appendPattern("HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter(Locale.ROOT)
        .withZone(ZoneId.systemDefault())
}

// Example result: 2023-07-07 14:42:29.343+02:00 or 2023-07-07 12:42:29.343Z
private val SQLITE_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatterBuilder()
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

// For UTC time zone, MySQL rejects the 'Z' and will only accept the offset '+00:00'
private val MYSQL_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalStart()
        .appendPattern("xxx")
        .optionalEnd()
        .toFormatter(Locale.ROOT)
}

// Example result: 2023-07-07 14:42:29.343789 +02:00
private val ORACLE_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendLiteral(' ')
        .optionalStart()
        .appendPattern("xxx")
        .optionalEnd()
        .toFormatter(Locale.ROOT)
}

// Example result: 2023-07-07 14:42:29.343
private val OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .toFormatter(Locale.ROOT)
}

private fun formatterForDateString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)

private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "yyyy-MM-dd HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormatter.ofPattern(newFormat).withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private fun oracleDateTimeLiteral(instant: Instant) =
    "TO_TIMESTAMP('${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampLiteral(instant: Instant) =
    "TO_TIMESTAMP('${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampWithTimezoneLiteral(dateTime: OffsetDateTime) =
    "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"

private fun oracleDateLiteral(instant: Instant) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD')"

private fun mysqlDateTimeAsDefaultFormatter(isFractionDateTimeSupported: Boolean, precision: Byte?) =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .apply {
            if (isFractionDateTimeSupported && precision != null && precision > 0) {
                this.appendFraction(ChronoField.NANO_OF_SECOND, precision.toInt(), precision.toInt(), true)
            }
        }
        .toFormatter(Locale.ROOT)

private val LocalDate.millis get() = atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample org.jetbrains.exposed.sql.javatime.date
 */
class JavaLocalDateColumnType : ColumnType<LocalDate>(), IDateColumnType {
    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: LocalDate): String {
        val instant = Instant.from(value.atStartOfDay(ZoneId.systemDefault()))
        return when (currentDialect) {
            is OracleDialect -> {
                // Date literal in Oracle DB must match NLS_DATE_FORMAT parameter.
                // That parameter can be changed on DB level.
                // But format can be also specified per literal with TO_DATE function
                oracleDateLiteral(instant)
            }
            else -> "'${DEFAULT_DATE_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): LocalDate? = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: LocalDate): Any = when (currentDialect) {
        is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.format(value)
        else -> java.sql.Date(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: LocalDate): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
        else -> super.nonNullValueAsDefaultString(value)
    }

    private fun longToLocalDate(instant: Long) = Instant.ofEpochMilli(instant).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        internal val INSTANCE = JavaLocalDateColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [LocalDateTime].
 *
 * @sample org.jetbrains.exposed.sql.javatime.datetime
 */
class JavaLocalDateTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<LocalDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType(precision)

    override fun nonNullValueToString(value: LocalDateTime): String {
        val instant = Instant.from(value.atZone(ZoneId.systemDefault()))

        return when (val dialect = currentDialect) {
            is SQLiteDialect -> "'${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant)}'"
            is OracleDialect -> oracleDateTimeLiteral(instant)
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_DATE_TIME_STRING_FORMATTER
                }
                "'${formatter.format(instant)}'"
            }
            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> LocalDateTime.parse(value, formatterForDateString(value))
        is OffsetDateTime -> value.toLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = when (currentDialect) {
        is SQLiteDialect -> DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value.atZone(ZoneId.systemDefault()))
        else -> {
            val instant = value.atZone(ZoneId.systemDefault()).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is OracleDialect -> rs.getObject(index, java.sql.Timestamp::class.java)
        else -> super.readObject(rs, index)
    }

    override fun nonNullValueAsDefaultString(value: LocalDateTime): String {
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect -> "'${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value)}'::timestamp without time zone"
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                    .withZone(ZoneId.systemDefault())

                if (dialect is MariaDBDialect) {
                    "'${formatter.format(value)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (value.nano / factor).roundToLong() * factor.toLong()
                        value.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        value.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (value.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
                }
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    private fun longToLocalDateTime(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    private fun longToLocalDateTime(seconds: Long, nanos: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneId.systemDefault())

    companion object {
        internal val INSTANCE = JavaLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample org.jetbrains.exposed.sql.javatime.time
 */
class JavaLocalTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType(precision)

    override fun nonNullValueToString(value: LocalTime): String =
        when (currentDialect) {
            is OracleDialect -> "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.format(value)}'"
            else -> "'${DEFAULT_TIME_STRING_FORMATTER.format(value)}'"
        }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect) {
                formatterForDateString(value)
            } else {
                DEFAULT_TIME_STRING_FORMATTER
            }
            LocalTime.parse(value, formatter)
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalTime): Any =
        when (val dialect = currentDialect) {
            is SQLiteDialect, is SQLServerDialect, is H2Dialect -> DEFAULT_TIME_STRING_FORMATTER.format(value)
            is MysqlDialect -> {
                if (dialect.isFractionDateTimeSupported()) {
                    DEFAULT_TIME_STRING_FORMATTER.format(value)
                } else {
                    java.sql.Time.valueOf(value)
                }
            }
            else -> value
        }

    override fun nonNullValueAsDefaultString(value: LocalTime): String =
        when (val dialect = currentDialect) {
            is PostgreSQLDialect -> {
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern("HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .toFormatter(Locale.ROOT)
                    .withZone(ZoneId.systemDefault())
                "'${formatter.format(value)}'::time without time zone"
            }
            is MysqlDialect -> {
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern("HH:mm:ss")
                    .apply {
                        if (dialect.isFractionDateTimeSupported() && precision != null && precision > 0) {
                            this.appendFraction(ChronoField.NANO_OF_SECOND, precision.toInt(), precision.toInt(), true)
                        }
                    }
                    .toFormatter(Locale.ROOT)
                    .withZone(ZoneId.systemDefault())

                if (dialect is MariaDBDialect) {
                    "'${formatter.format(value)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (value.nano / factor).roundToLong() * factor.toLong()
                        value.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        value.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (value.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
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

    private fun longToLocalTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

    companion object {
        internal val INSTANCE = JavaLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [Instant].
 *
 * @sample org.jetbrains.exposed.sql.javatime.timestamp
 */
class JavaInstantColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<Instant>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType(precision)

    override fun nonNullValueToString(value: Instant): String {
        return when (val dialect = currentDialect) {
            is OracleDialect -> oracleTimestampLiteral(value)
            is SQLiteDialect -> "'${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value)}'"
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_TIMESTAMP_STRING_FORMATTER
                }
                "'${formatter.format(value)}'"
            }
            else -> "'${DEFAULT_TIMESTAMP_STRING_FORMATTER.format(value)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is Instant -> value
        is java.sql.Timestamp -> value.toInstant()
        is String -> when (currentDialect) {
            is SQLiteDialect -> TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.parse(value, java.time.Instant::from)
            else -> Instant.parse(value)
        }
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        else -> rs.getTimestamp(index)
    }

    override fun notNullValueToDB(value: Instant): Any {
        val dialect = currentDialect
        return when {
            dialect is SQLiteDialect -> TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value)
            dialect is MysqlDialect && dialect !is MariaDBDialect && !TransactionManager.current().db.isVersionCovers(8, 0) -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_TIMESTAMP_STRING_FORMATTER
                }
                formatter.format(value)
            }
            else -> java.sql.Timestamp.from(value)
        }
    }

    override fun nonNullValueAsDefaultString(value: Instant): String {
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect ->
                "'${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value)}'::timestamp without time zone"
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                    .withZone(UTC)

                if (dialect is MariaDBDialect) {
                    "'${formatter.format(value)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (value.nano / factor).roundToLong() * factor.toLong()
                        value.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        value.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (value.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
                }
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = JavaInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
 */
class JavaOffsetDateTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<OffsetDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType(precision)

    override fun nonNullValueToString(value: OffsetDateTime): String = when (currentDialect) {
        is SQLiteDialect -> "'${value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        is MysqlDialect -> "'${value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)}'"
        is OracleDialect -> oracleTimestampWithTimezoneLiteral(value)
        else -> "'${value.format(DEFAULT_OFFSET_DATE_TIME_FORMATTER)}'"
    }

    override fun valueFromDB(value: Any): OffsetDateTime = when (value) {
        is OffsetDateTime -> value
        is ZonedDateTime -> value.toOffsetDateTime()
        is String -> {
            if (currentDialect is SQLiteDialect) {
                val temporalAccessor = SQLITE_OFFSET_DATE_TIME_FORMATTER.parse(value)
                if (temporalAccessor.isSupported(ChronoField.OFFSET_SECONDS)) {
                    OffsetDateTime.from(temporalAccessor)
                } else {
                    OffsetDateTime.from(LocalDateTime.from(temporalAccessor).atOffset(UTC))
                }
            } else {
                OffsetDateTime.parse(value)
            }
        }
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, ZonedDateTime::class.java)
        else -> rs.getObject(index, OffsetDateTime::class.java)
    }

    override fun notNullValueToDB(value: OffsetDateTime): Any = when (currentDialect) {
        is SQLiteDialect -> value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)
        is MysqlDialect -> value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)
        else -> value
    }

    override fun nonNullValueAsDefaultString(value: OffsetDateTime): String {
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect -> // +00 appended because PostgreSQL stores it in UTC time zone
                "'${value.format(OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}+00'::timestamp with time zone"
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)

                val roundedValue = if (precision != null && precision > 0) {
                    val factor = (10f).pow(9 - precision.toInt())
                    val roundedNanos = (value.nano / factor).roundToLong() * factor.toLong()
                    value.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                } else {
                    value.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (value.nano >= 500_000_000) 1 else 0)
                }
                "'${formatter.format(roundedValue)}'"
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = JavaOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample org.jetbrains.exposed.sql.javatime.duration
 */
class JavaDurationColumnType : ColumnType<Duration>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Duration): String = "'${value.toNanos()}'"

    override fun valueFromDB(value: Any): Duration = when (value) {
        is Long -> Duration.ofNanos(value)
        is Number -> Duration.ofNanos(value.toLong())
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // ResultSet.getLong returns 0 instead of null
        return rs.getLong(index).takeIf { rs.getObject(index) != null }
    }

    override fun notNullValueToDB(value: Duration): Any = value.toNanos()

    companion object {
        internal val INSTANCE = JavaDurationColumnType()
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, JavaLocalDateColumnType())

/**
 * A datetime column to store both a date and a time without time zone.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.datetime(name: String, precision: Byte? = null): Column<LocalDateTime> = registerColumn(name, JavaLocalDateTimeColumnType(precision))

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 *
 * @author Maxim Vorotynsky
 */
fun Table.time(name: String, precision: Byte? = null): Column<LocalTime> = registerColumn(name, JavaLocalTimeColumnType(precision))

/**
 * A timestamp column to store both a date and a time without time zone.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.timestamp(name: String, precision: Byte? = null): Column<Instant> = registerColumn(name, JavaInstantColumnType(precision))

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.timestampWithTimeZone(name: String, precision: Byte? = null): Column<OffsetDateTime> =
    registerColumn(name, JavaOffsetDateTimeColumnType(precision))

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, JavaDurationColumnType())
