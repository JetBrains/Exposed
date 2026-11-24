package org.jetbrains.exposed.v1.core.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import java.sql.Date
import java.sql.Timestamp
import kotlin.time.Instant

private fun formatDate(instant: Instant, timeZone: TimeZone): String {
    return LocalDate.Formats.ISO.format(instant.toLocalDateTime(timeZone).date)
}

private fun oracleDateLiteral(instant: Instant): String {
    val formatted = formatDate(instant, TimeZone.currentSystemDefault())
    return "TO_DATE('$formatted', 'YYYY-MM-DD')"
}

/**
 * Base column type for storing date values without time components.
 *
 * This abstract class provides the foundation for date-only columns across different database dialects.
 * It handles the conversion between application date types and database representations, ensuring
 * proper formatting and parsing based on the current database dialect.
 *
 * @param T The application-specific date type (e.g., [LocalDate], kotlinx.datetime.LocalDate)
 * @see IDateColumnType
 * @see KotlinLocalDateColumnType
 */
abstract class LocalDateColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalDate(value: T): LocalDate

    abstract fun fromLocalDate(value: LocalDate): T

    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: T & Any): String {
        val localDate = toLocalDate(value)
        val instant = localDate.atStartOfDayIn(TimeZone.currentSystemDefault())

        return if (currentDialect is OracleDialect) {
            oracleDateLiteral(instant)
        } else {
            "'${formatDate(instant, TimeZone.currentSystemDefault())}'"
        }
    }

    private fun localDateValueFromDB(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is Date -> longToLocalDate(value.time)
        is Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun valueFromDB(value: Any): T? {
        return fromLocalDate(localDateValueFromDB(value))
    }

    private fun longToLocalDate(epochMillis: Long): LocalDate {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val localDate = toLocalDate(value)
        val instant = localDate.atStartOfDayIn(TimeZone.currentSystemDefault())

        return when {
            currentDialect is SQLiteDialect -> formatDate(instant, TimeZone.currentSystemDefault())
            else -> {
                require(instant.isRepresentableAsEpochMillis()) {
                    "Value $localDate is out of range for a DATE column: it is beyond what a JDBC date parameter can " +
                        "represent (roughly years -292275055 to 292278994). Binding it would silently send a different " +
                        "date to the database, making the condition match unrelated rows or none at all. " +
                        "Use a date within the supported range."
                }
                Date(instant.toEpochMilliseconds())
            }
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        return when (currentDialect) {
            is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
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

private fun LocalDate.atStartOfDayIn(timeZone: TimeZone): Instant {
    return this.atTime(LocalTime(0, 0, 0)).toInstant(timeZone)
}

private val MIN_MILLIS_INSTANT = Instant.fromEpochMilliseconds(Long.MIN_VALUE)
private val MAX_MILLIS_INSTANT = Instant.fromEpochMilliseconds(Long.MAX_VALUE)

/**
 * Returns whether this instant survives conversion to epoch milliseconds and back unchanged.
 *
 * JDBC date and timestamp parameters carry values as milliseconds since the epoch, so instants outside this range are
 * silently replaced with the closest representable value when bound. The database then receives a different value than
 * the one supplied, which makes the surrounding condition match rows it should not (or none at all).
 */
internal fun Instant.isRepresentableAsEpochMillis(): Boolean = this >= MIN_MILLIS_INSTANT && this <= MAX_MILLIS_INSTANT
