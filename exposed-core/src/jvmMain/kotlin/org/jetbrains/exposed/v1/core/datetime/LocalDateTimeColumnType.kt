package org.jetbrains.exposed.v1.core.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private val SQLITE_ORACLE_DATETIME_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter(fraction = 3) }

private val MYSQL_FRACTION_DATETIME_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter(fraction = 6) }

private val MYSQL_DATETIME_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter() }

private val DEFAULT_DATETIME_FORMAT: DateTimeFormat<LocalDateTime> by lazy {
    LocalDateTime.Format {
        date(LocalDate.Formats.ISO)
        char('T')
        time(LocalTime.Formats.ISO)
    }
}

/**
 * Base column type for storing date and time values without timezone information.
 *
 * This abstract class handles datetime columns that store both date and time components
 * but do not preserve timezone information. The values are interpreted in the system's
 * default timezone when converting to/from database representations.
 *
 * @param T The application-specific datetime type (e.g., [LocalDateTime], kotlinx.datetime.LocalDateTime)
 * @see KotlinLocalDateTimeColumnType
 */
abstract class LocalDateTimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalDateTime(value: T): LocalDateTime

    abstract fun fromLocalDateTime(value: LocalDateTime): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: T & Any): String {
        val dateTime = toLocalDateTime(value)
            .toInstant(TimeZone.currentSystemDefault())
            .toLocalDateTime(TimeZone.currentSystemDefault())

        return when (val dialect = currentDialect) {
            is SQLiteDialect -> "'${SQLITE_ORACLE_DATETIME_FORMAT.format(dateTime)}'"
            is OracleDialect -> {
                val formatted = SQLITE_ORACLE_DATETIME_FORMAT.format(dateTime)
                "TO_TIMESTAMP('$formatted', 'YYYY-MM-DD HH24:MI:SS.FF3')"
            }
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    MYSQL_FRACTION_DATETIME_FORMAT
                } else {
                    MYSQL_DATETIME_FORMAT
                }

                "'${formatter.format(dateTime)}'"
            }
            else -> "'${DEFAULT_DATETIME_FORMAT.format(dateTime)}'"
        }
    }

    @Suppress("MagicNumber")
    private fun localDateTimeValueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> parseLocalDateTime(value)
        is java.time.OffsetDateTime -> {
            // It looks like there is no direct coversion between OffsetDateTime and anything from kotlin datetime modules,
            //  so here conversion happens via java.time.Instant
            value.toInstant()
                .toKotlinInstant()
                .toLocalDateTime(TimeZone.currentSystemDefault())
        }

        else -> localDateTimeValueFromDB(value.toString())
    }

    override fun valueFromDB(value: Any): T? {
        return localDateTimeValueFromDB(value)?.let { fromLocalDateTime(it) }
    }

    private fun longToLocalDateTime(millis: Long): LocalDateTime {
        return Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    }

    private fun longToLocalDateTime(seconds: Long, nanos: Long): LocalDateTime {
        return Instant.fromEpochSeconds(epochSeconds = seconds, nanosecondAdjustment = nanos)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }

    private fun parseLocalDateTime(value: String): LocalDateTime {
        return try {
            // Try with fractions first
            val fractionLength = value.substringAfterLast('.', "").length
            // Formatter must be non-lazy due to concurrency safety
            createLocalDateTimeFormatter(fractionLength).parse(value)
        } catch (_: IllegalArgumentException) {
            // Fallback to ISO parsing
            LocalDateTime.parse(value)
        }
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val localDateTime = toLocalDateTime(value)

        return when {
            currentDialect is SQLiteDialect -> {
                SQLITE_ORACLE_DATETIME_FORMAT.format(localDateTime)
            }
            else -> localDateTime.toSqlTimestamp()
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return if (currentDialect is OracleDialect) {
            rs.getObject(index, java.sql.Timestamp::class.java)
        } else {
            super.readObject(rs, index)
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val dialect = currentDialect
        val localDateTime = toLocalDateTime(value)

        return when {
            dialect is PostgreSQLDialect -> {
                val formatted = SQLITE_ORACLE_DATETIME_FORMAT.format(localDateTime)
                "'${formatted.trimEnd('0').trimEnd('.')}'::timestamp without time zone"
            }
            (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> {
                val formatted = SQLITE_ORACLE_DATETIME_FORMAT.format(localDateTime)
                "'${formatted.trimEnd('0').trimEnd('.')}'"
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}

internal fun createLocalDateTimeFormatter(fraction: Int = 0) = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
    if (fraction in 1..9) {
        char('.')
        secondFraction(fraction)
    }
}

internal fun LocalDateTime.toSqlTimestamp(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
) = toInstant(timeZone).let {
    java.sql.Timestamp(it.toEpochMilliseconds())
        .apply { this.nanos = it.nanosecondsOfSecond }
}
