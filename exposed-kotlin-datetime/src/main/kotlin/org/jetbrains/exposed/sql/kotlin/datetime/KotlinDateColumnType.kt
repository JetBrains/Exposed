@file:Suppress("PrivatePropertyName")

package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import java.sql.ResultSet
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
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
private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
}

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "1900-01-01 HH:mm:ss",
        Locale.ROOT
    ).withZone(ZoneId.of("UTC"))
}

private val DEFAULT_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private fun formatterForDateString(date: String) = dateTimeWithFractionFormat(date.substringAfterLast('.', "").length)
private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "yyyy-MM-d HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormatter.ofPattern(newFormat).withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val LocalDate.millis get() = this.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds * MILLIS_IN_SECOND

class KotlinLocalDateColumnType : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = false

    override fun sqlType(): String = "DATE"

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDate -> Instant.fromEpochMilliseconds(value.atStartOfDayIn(DEFAULT_TIME_ZONE).toEpochMilliseconds())
            is java.sql.Date -> Instant.fromEpochMilliseconds(value.time)
            is java.sql.Timestamp -> Instant.fromEpochSeconds(value.time / MILLIS_IN_SECOND, value.nanos.toLong())
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'${DEFAULT_DATE_STRING_FORMATTER.format(instant.toJavaInstant())}'"
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> when (currentDialect) {
            is SQLiteDialect -> LocalDate.parse(value)
            else -> value
        }
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: Any) = when (value) {
        is LocalDate -> java.sql.Date(value.millis)
        else -> value
    }

    private fun longToLocalDate(instant: Long) = Instant.fromEpochMilliseconds(instant).toLocalDateTime(DEFAULT_TIME_ZONE).date

    companion object {
        internal val INSTANCE = KotlinLocalDateColumnType()
    }
}

class KotlinLocalDateTimeColumnType : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true
    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDateTime -> value.toInstant(DEFAULT_TIME_ZONE)
            is java.sql.Date -> Instant.fromEpochMilliseconds(value.time)
            is java.sql.Timestamp -> Instant.fromEpochSeconds(value.time / MILLIS_IN_SECOND, value.nanos.toLong())
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        val dialect = currentDialect

        return when {
            dialect is SQLiteDialect -> "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
            dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
        }
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / MILLIS_IN_SECOND, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is java.time.LocalDateTime -> value.toKotlinLocalDateTime()
        is String -> java.time.LocalDateTime.parse(value, formatterForDateString(value)).toKotlinLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is LocalDateTime && currentDialect is SQLiteDialect ->
            SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()))
        value is LocalDateTime -> {
            val instant = value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
        else -> value
    }

    private fun longToLocalDateTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE)
    private fun longToLocalDateTime(seconds: Long, nanos: Long) = Instant.fromEpochSeconds(seconds, nanos).toLocalDateTime(DEFAULT_TIME_ZONE)

    companion object {
        internal val INSTANCE = KotlinLocalDateTimeColumnType()
    }
}

class KotlinLocalTimeColumnType : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: Any): String {
        val formatter = if (currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            ORACLE_TIME_STRING_FORMATTER
        } else {
            DEFAULT_TIME_STRING_FORMATTER
        }

        val instant = when (value) {
            is String -> return value
            is LocalTime -> value.toJavaLocalTime()
            is java.sql.Time -> Instant.fromEpochMilliseconds(value.time).toJavaInstant()
            is java.sql.Timestamp -> Instant.fromEpochSeconds(value.time / MILLIS_IN_SECOND, value.nanos.toLong()).toJavaInstant()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }
        return "'${formatter.format(instant)}'"
    }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().toKotlinLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime().toKotlinLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val formatter = if (currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                formatterForDateString(value)
            } else {
                DEFAULT_TIME_STRING_FORMATTER
            }
            java.time.LocalTime.parse(value, formatter).toKotlinLocalTime()
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any = when (value) {
        is LocalTime -> java.sql.Time.valueOf(value.toJavaLocalTime())
        else -> value
    }

    private fun longToLocalTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE).time

    companion object {
        internal val INSTANCE = KotlinLocalTimeColumnType()
    }
}

class KotlinInstantColumnType : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true
    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is Instant -> value.toJavaInstant()
            is java.sql.Timestamp -> value.toInstant()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return when {
            currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant)}'"
            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is java.sql.Timestamp -> value.toInstant().toKotlinInstant()
        is String -> Instant.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getTimestamp(index)
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is Instant && currentDialect is SQLiteDialect ->
            SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value.toJavaInstant())
        value is Instant ->
            java.sql.Timestamp.from(value.toJavaInstant())
        else -> value
    }

    companion object {
        internal val INSTANCE = KotlinInstantColumnType()
    }
}

class KotlinDurationColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Any): String {
        val duration = when (value) {
            is String -> return value
            is Duration -> value.inWholeNanoseconds
            is Long -> value
            is Number -> value.toLong()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'$duration'"
    }

    override fun valueFromDB(value: Any): Duration = when (value) {
        is Long -> value.nanoseconds
        is Number -> value.toLong().nanoseconds
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // ResultSet.getLong returns 0 instead of null
        return rs.getLong(index).takeIf { rs.getObject(index) != null }
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is Duration) {
            return value.inWholeNanoseconds
        }
        return value
    }

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
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<LocalDateTime> = registerColumn(name, KotlinLocalDateTimeColumnType())

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 */
fun Table.time(name: String): Column<LocalTime> = registerColumn(name, KotlinLocalTimeColumnType())

/**
 * A timestamp column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.timestamp(name: String): Column<Instant> = registerColumn(name, KotlinInstantColumnType())

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, KotlinDurationColumnType())
