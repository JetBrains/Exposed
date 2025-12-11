package org.jetbrains.exposed.v1.core.datetime

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.vendors.*
import java.sql.Timestamp
import java.time.ZoneId
import kotlin.time.Instant

@Suppress("MagicNumber")
private val MYSQL_TIMESTAMP_FRACTION_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter(fraction = 6) }

private val MYSQL_TIMESTAMP_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter() }

@Suppress("MagicNumber")
private val ORACLE_SQLITE_TIMESTAMP_FORMAT: DateTimeFormat<LocalDateTime> by lazy { createLocalDateTimeFormatter(3) }

private val DEFAULT_TIMESTAMP_FORMAT: DateTimeFormat<LocalDateTime> = LocalDateTime.Formats.ISO

/**
 * Base column type for storing timestamp values representing instants in time.
 *
 * This abstract class handles timestamp columns that store precise moments in time,
 * typically represented as instants since the Unix epoch. Unlike datetime columns,
 * timestamps are timezone-aware and represent absolute points in time.
 *
 * @param T The application-specific instant type (e.g., [Instant], kotlin.time.Instant)
 * @see IDateColumnType
 * @see KotlinInstantColumnType
 */
abstract class InstantColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toInstant(value: T): Instant

    abstract fun fromInstant(instant: Instant): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType()

    override fun nonNullValueToString(value: T & Any): String {
        val localDateTime = toInstant(value).toLocalDateTime(TimeZone.currentSystemDefault())

        return when (val dialect = currentDialect) {
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) {
                    MYSQL_TIMESTAMP_FRACTION_FORMAT
                } else {
                    MYSQL_TIMESTAMP_FORMAT
                }
                "'${formatter.format(localDateTime)}'"
            }
            is SQLiteDialect -> "'${ORACLE_SQLITE_TIMESTAMP_FORMAT.format(localDateTime)}'"
            is OracleDialect -> {
                val formatted = ORACLE_SQLITE_TIMESTAMP_FORMAT.format(localDateTime)
                "TO_TIMESTAMP('$formatted', 'YYYY-MM-DD HH24:MI:SS.FF3')"
            }
            else -> "'${DEFAULT_TIMESTAMP_FORMAT.format(localDateTime)}'"
        }
    }

    @Suppress("MagicNumber")
    private fun instantValueFromDB(value: Any): Instant = when (value) {
        is Timestamp -> Instant.fromEpochSeconds(value.time / 1000, value.nanos)
        is String -> parseInstantFromString(value)
        is java.time.LocalDateTime -> {
            value.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
                .let { Instant.fromEpochMilliseconds(it) }
        }
        else -> instantValueFromDB(value.toString())
    }

    private fun parseInstantFromString(value: String): Instant {
        return try {
            val javaInstant = java.time.Instant.parse(value)
            Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano.toLong())
        } catch (e: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Failed to parse instant from string: $value", e)
        }
    }

    override fun valueFromDB(value: Any): T {
        return fromInstant(instantValueFromDB(value))
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return rs.getObject(index, Timestamp::class.java, this)
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val localDateTime = toInstant(value).toLocalDateTime(TimeZone.currentSystemDefault())

        @OptIn(InternalApi::class)
        @Suppress("MagicNumber")
        return when (val dialect = currentDialect) {
            is SQLiteDialect -> ORACLE_SQLITE_TIMESTAMP_FORMAT.format(localDateTime)
            is MysqlDialect if (
                dialect !is MariaDBDialect &&
                    !currentTransaction().db.version.covers(8, 0)
                ) -> {
                if (dialect.isFractionDateTimeSupported()) {
                    MYSQL_TIMESTAMP_FRACTION_FORMAT.format(localDateTime)
                } else {
                    MYSQL_TIMESTAMP_FORMAT.format(localDateTime)
                }
            }
            else -> localDateTime.toSqlTimestamp()
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val dialect = currentDialect

        val localDateTime = toInstant(value).toLocalDateTime(TimeZone.currentSystemDefault())

        return when {
            dialect is PostgreSQLDialect -> {
                val formatted = ORACLE_SQLITE_TIMESTAMP_FORMAT.format(localDateTime)
                "'${formatted.trimEnd('0').trimEnd('.')}'::timestamp without time zone"
            }
            dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> {
                val formatted = ORACLE_SQLITE_TIMESTAMP_FORMAT.format(localDateTime)
                "'${formatted.trimEnd('0').trimEnd('.')}'"
            }
            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}
