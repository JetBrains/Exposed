@file: Suppress("MagicNumber")

package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val MILLIS_IN_SECOND = 1000

private val DEFAULT_TIME_ZONE by lazy {
    TimeZone.currentSystemDefault()
}

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

private fun formatterForDateTimeString(date: String) = dateTimeWithFractionFormat(
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
    "TO_TIMESTAMP('${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampLiteral(instant: Instant) =
    "TO_TIMESTAMP('${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleDateTimeWithTimezoneLiteral(dateTime: OffsetDateTime) =
    "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"

private fun oracleDateLiteral(instant: Instant) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD')"

private fun mysqlDateTimeAsDefaultFormatter(isFractionDateTimeSupported: Boolean, precision: Byte?) =
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .apply {
            if (isFractionDateTimeSupported && precision != null && precision > 0) {
                this.appendFraction(ChronoField.NANO_OF_SECOND, precision.toInt(), precision.toInt(), true)
            }
        }
        .toFormatter(Locale.ROOT)

private val LocalDate.millis get() = this.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds * MILLIS_IN_SECOND

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.date
 */
class KotlinLocalDateColumnType : ColumnType<LocalDate>(), IDateColumnType {
    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: LocalDate): String {
        val instant = Instant.fromEpochMilliseconds(value.atStartOfDayIn(DEFAULT_TIME_ZONE).toEpochMilliseconds())
        return when (currentDialect) {
            is OracleDialect -> oracleDateLiteral(instant)
            else -> "'${DEFAULT_DATE_STRING_FORMATTER.format(instant.toJavaInstant())}'"
        }
    }

    override fun valueFromDB(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: LocalDate): Any = when (currentDialect) {
        is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.format(value.toJavaLocalDate())
        else -> java.sql.Date(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: LocalDate): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
        else -> super.nonNullValueAsDefaultString(value)
    }

    private fun longToLocalDate(instant: Long) = Instant.fromEpochMilliseconds(instant).toLocalDateTime(DEFAULT_TIME_ZONE).date

    companion object {
        internal val INSTANCE = KotlinLocalDateColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [LocalDateTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.datetime
 */
class KotlinLocalDateTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<LocalDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType(precision)

    override fun nonNullValueToString(value: LocalDateTime): String {
        val instant = value.toInstant(DEFAULT_TIME_ZONE)

        return when (val dialect = currentDialect) {
            is SQLiteDialect -> "'${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant.toJavaInstant())}'"
            is OracleDialect -> oracleDateTimeLiteral(instant)
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_DATE_TIME_STRING_FORMATTER
                }
                "'${formatter.format(instant.toJavaInstant())}'"
            }
            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
        }
    }

    override fun valueFromDB(value: Any): LocalDateTime = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / MILLIS_IN_SECOND, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is java.time.LocalDateTime -> value.toKotlinLocalDateTime()
        is String -> java.time.LocalDateTime.parse(value, formatterForDateTimeString(value)).toKotlinLocalDateTime()
        is OffsetDateTime -> value.toLocalDateTime().toKotlinLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = when (currentDialect) {
        is SQLiteDialect -> DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()))
        else -> {
            val instant = value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is OracleDialect -> rs.getObject(index, java.sql.Timestamp::class.java)
        else -> super.readObject(rs, index)
    }

    override fun nonNullValueAsDefaultString(value: LocalDateTime): String {
        val javaInstant = value.toInstant(DEFAULT_TIME_ZONE).toJavaInstant()
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect -> "'${DATE_TIME_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(javaInstant)}'::timestamp without time zone"
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                    .withZone(ZoneId.systemDefault())

                if (dialect is MariaDBDialect) {
                    "'${formatter.format(javaInstant)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (javaInstant.nano / factor).roundToLong() * factor.toLong()
                        javaInstant.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        javaInstant.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (javaInstant.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
                }
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    private fun longToLocalDateTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE)

    private fun longToLocalDateTime(seconds: Long, nanos: Long) = Instant.fromEpochSeconds(seconds, nanos).toLocalDateTime(DEFAULT_TIME_ZONE)

    companion object {
        internal val INSTANCE = KotlinLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.time
 */
class KotlinLocalTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType(precision)

    override fun nonNullValueToString(value: LocalTime): String {
        val instant = value.toJavaLocalTime()
        return when (currentDialect) {
            is OracleDialect -> "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.format(instant)}'"
            else -> "'${DEFAULT_TIME_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().toKotlinLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime().toKotlinLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect) {
                formatterForDateTimeString(value)
            } else {
                DEFAULT_TIME_STRING_FORMATTER
            }
            java.time.LocalTime.parse(value, formatter).toKotlinLocalTime()
        }

        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalTime): Any =
        when (val dialect = currentDialect) {
            is SQLiteDialect, is SQLServerDialect, is H2Dialect -> DEFAULT_TIME_STRING_FORMATTER.format(value.toJavaLocalTime())
            is MysqlDialect -> {
                if (dialect.isFractionDateTimeSupported()) {
                    DEFAULT_TIME_STRING_FORMATTER.format(value.toJavaLocalTime())
                } else {
                    java.sql.Time.valueOf(value.toJavaLocalTime())
                }
            }
            else -> value.toJavaLocalTime()
        }

    override fun nonNullValueAsDefaultString(value: LocalTime): String {
        val javaLocalTime = value.toJavaLocalTime()
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect -> {
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern("HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .toFormatter(Locale.ROOT)
                    .withZone(ZoneId.systemDefault())
                "'${formatter.format(javaLocalTime)}'::time without time zone"
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
                    "'${formatter.format(javaLocalTime)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (javaLocalTime.nano / factor).roundToLong() * factor.toLong()
                        javaLocalTime.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        javaLocalTime.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (javaLocalTime.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
                }
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
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

    private fun longToLocalTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE).time

    companion object {
        internal val INSTANCE = KotlinLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [Instant].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.timestamp
 */
class KotlinInstantColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<Instant>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType(precision)

    override fun nonNullValueToString(value: Instant): String {
        val instant = value.toJavaInstant()

        return when (val dialect = currentDialect) {
            is OracleDialect -> oracleTimestampLiteral(value)
            is SQLiteDialect -> "'${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(instant)}'"
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_TIMESTAMP_STRING_FORMATTER
                }
                "'${formatter.format(instant)}'"
            }
            else -> "'${DEFAULT_TIMESTAMP_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is Instant -> value
        is java.time.Instant -> value.toKotlinInstant()
        is java.sql.Timestamp -> value.toInstant().toKotlinInstant()
        is String -> when (currentDialect) {
            is SQLiteDialect -> TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.parse(value, java.time.Instant::from).toKotlinInstant()
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
            dialect is SQLiteDialect -> TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(value.toJavaInstant())
            dialect is MysqlDialect && dialect !is MariaDBDialect && !TransactionManager.current().db.isVersionCovers(8, 0) -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER
                } else {
                    MYSQL_TIMESTAMP_STRING_FORMATTER
                }
                formatter.format(value.toJavaInstant())
            }
            else -> java.sql.Timestamp.from(value.toJavaInstant())
        }
    }

    override fun nonNullValueAsDefaultString(value: Instant): String {
        val javaInstant = value.toJavaInstant()
        return when (val dialect = currentDialect) {
            is PostgreSQLDialect ->
                "'${TIMESTAMP_WITH_FRACTIONAL_SECONDS_STRING_FORMATTER.format(javaInstant)}'::timestamp without time zone"
            is MysqlDialect -> {
                val formatter = mysqlDateTimeAsDefaultFormatter(dialect.isFractionDateTimeSupported(), precision)
                    .withZone(UTC)

                if (dialect is MariaDBDialect) {
                    "'${formatter.format(javaInstant)}'"
                } else {
                    val roundedValue = if (precision != null && precision > 0) {
                        val factor = (10f).pow(9 - precision.toInt())
                        val roundedNanos = (javaInstant.nano / factor).roundToLong() * factor.toLong()
                        javaInstant.with(ChronoField.NANO_OF_SECOND, roundedNanos)
                    } else {
                        javaInstant.truncatedTo(ChronoUnit.SECONDS).plusSeconds(if (javaInstant.nano >= 500_000_000) 1 else 0)
                    }
                    "'${formatter.format(roundedValue)}'"
                }
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = KotlinInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
 */
class KotlinOffsetDateTimeColumnType(
    /** Fractional seconds precision */
    val precision: Byte? = null
) : ColumnType<OffsetDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType(precision)

    override fun nonNullValueToString(value: OffsetDateTime): String = when (currentDialect) {
        is SQLiteDialect -> "'${value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        is MysqlDialect -> "'${value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)}'"
        is OracleDialect -> oracleDateTimeWithTimezoneLiteral(value)
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
                    OffsetDateTime.from(java.time.LocalDateTime.from(temporalAccessor).atOffset(UTC))
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
        internal val INSTANCE = KotlinOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.duration
 */
class KotlinDurationColumnType : ColumnType<Duration>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Duration): String {
        val duration = value.inWholeNanoseconds
        return "'$duration'"
    }

    override fun valueFromDB(value: Any): Duration = when (value) {
        Duration.INFINITE.inWholeNanoseconds -> Duration.INFINITE
        is Long -> value.nanoseconds
        is Number -> value.toLong().nanoseconds
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // ResultSet.getLong returns 0 instead of null
        return rs.getLong(index).takeIf { rs.getObject(index) != null }
    }

    override fun notNullValueToDB(value: Duration): Any = value.inWholeNanoseconds

    companion object {
        internal val INSTANCE = KotlinDurationColumnType()
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, KotlinLocalDateColumnType())

/**
 * A datetime column to store both a date and a time without time zone.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.datetime(name: String, precision: Byte? = null): Column<LocalDateTime> = registerColumn(name, KotlinLocalDateTimeColumnType(precision))

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.time(name: String, precision: Byte? = null): Column<LocalTime> = registerColumn(name, KotlinLocalTimeColumnType(precision))

/**
 * A timestamp column to store both a date and a time without time zone.
 *
 * @param name The column name
 * @param precision The fractional seconds precision
 */
fun Table.timestamp(name: String, precision: Byte? = null): Column<Instant> = registerColumn(name, KotlinInstantColumnType(precision))

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
    registerColumn(name, KotlinOffsetDateTimeColumnType(precision))

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, KotlinDurationColumnType())
