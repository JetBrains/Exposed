package org.jetbrains.exposed.v1.core.datetime

import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.vendors.*
import java.sql.Timestamp
import kotlin.time.Instant

private val ORACLE_TIME_FORMAT: DateTimeFormat<LocalDateTime> by lazy {
    LocalDateTime.Format {
        chars("1970-01-01 ")
        hour()
        char(':')
        minute()
        char(':')
        second()
    }
}

private val MYSQL_TIME_FORMAT: DateTimeFormat<LocalTime> by lazy {
    LocalTime.Format {
        hour()
        char(':')
        minute()
        char(':')
        second()
    }
}

@Suppress("MagicNumber")
private fun formatOracleTime(time: LocalTime): String {
    return ORACLE_TIME_FORMAT.format(LocalDateTime(LocalDate(1970, 1, 1), time))
}

private fun formatDefaultTime(time: LocalTime): String {
    return LocalTime.Formats.ISO.format(time)
}

private fun formatMySqlTime(time: LocalTime): String {
    return MYSQL_TIME_FORMAT.format(time)
}

/**
 * Base column type for storing time-of-day values without date components.
 *
 * This abstract class handles time-only columns that store hours, minutes, seconds,
 * and optionally fractional seconds, but no date information. Times are typically
 * interpreted within a day context.
 *
 * @param T The application-specific time type (e.g., [LocalTime], kotlinx.datetime.LocalTime)
 * @see IDateColumnType
 * @see KotlinLocalTimeColumnType
 */
abstract class LocalTimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalTime(value: T): LocalTime

    abstract fun fromLocalTime(value: LocalTime): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: T & Any): String {
        val localTime = toLocalTime(value)
        val dialect = currentDialect
        if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            return "TIMESTAMP '${formatOracleTime(localTime)}'"
        }
        return "'${formatDefaultTime(localTime)}'"
    }

    private fun localTimeValueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().toKotlinLocalTime()
        is Timestamp -> value.toLocalDateTime().toLocalTime().toKotlinLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> LocalTime.parse(value, LocalTime.Formats.ISO)
        else -> localTimeValueFromDB(value.toString())
    }

    private fun longToLocalTime(millis: Long): LocalTime =
        Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).time

    override fun valueFromDB(value: Any): T? {
        return fromLocalTime(localTimeValueFromDB(value))
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val localTimeValue = toLocalTime(value)

        return when {
            currentDialect is SQLiteDialect -> formatDefaultTime(localTimeValue)
            currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                Timestamp.valueOf(formatOracleTime(localTimeValue)).toInstant()
            else -> java.sql.Time.valueOf(localTimeValue.toJavaLocalTime())
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val localTime = toLocalTime(value)
        return when (currentDialect) {
            is PostgreSQLDialect -> "${nonNullValueToString(value)}::time without time zone"
            is MysqlDialect -> "'${formatMySqlTime(localTime)}'"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            rs.getObject(index, Timestamp::class.java, this)
        } else {
            super.readObject(rs, index)
        }
    }
}
