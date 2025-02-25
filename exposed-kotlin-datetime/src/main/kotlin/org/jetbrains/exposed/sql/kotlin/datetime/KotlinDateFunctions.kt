@file:Suppress("FunctionName")

package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.*
import java.time.OffsetDateTime
import kotlin.time.Duration

internal class DateInternal(val expr: Expression<*>) : Function<LocalDate>(KotlinLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.date(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the date part from a given [expr]. */
@JvmName("LocalDateDateFunction")
fun <T : LocalDate?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)

/** Represents an SQL function that extracts the date part from a given datetime [expr]. */
@JvmName("LocalDateTimeDateFunction")
fun <T : LocalDateTime?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)

/** Represents an SQL function that extracts the date part from a given timestamp [expr]. */
@JvmName("InstantDateFunction")
fun <T : Instant?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)

/** Represents an SQL function that extracts the date part from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeDateFunction")
fun <T : OffsetDateTime?> Date(expr: Expression<T>): Function<LocalDate> = DateInternal(expr)

internal class TimeInternal(val expr: Expression<*>, precision: Byte? = null) : Function<LocalTime>(KotlinLocalTimeColumnType(precision)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer,
            H2Dialect.H2CompatibilityMode.PostgreSQL,
            H2Dialect.H2CompatibilityMode.Oracle -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.time(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the time part from a given date [expr]. */
@JvmName("LocalDateTimeFunction")
fun <T : LocalDate?> Time(expr: Expression<T>): Function<LocalTime> = TimeInternal(expr)

/** Represents an SQL function that extracts the time part from a given datetime [expr], with fractional seconds [precision]. */
@JvmName("LocalDateTimeTimeFunction")
fun <T : LocalDateTime?> Time(expr: Expression<T>, precision: Byte? = null): Function<LocalTime> = TimeInternal(expr, precision)

/** Represents an SQL function that extracts the time part from a given timestamp [expr], with fractional seconds [precision]. */
@JvmName("InstantTimeFunction")
fun <T : Instant?> Time(expr: Expression<T>, precision: Byte? = null): Function<LocalTime> = TimeInternal(expr, precision)

/** Represents an SQL function that extracts the time part from a given timestampWithTimeZone [expr], with fractional seconds [precision]. */
@JvmName("OffsetDateTimeTimeFunction")
fun <T : OffsetDateTime?> Time(expr: Expression<T>, precision: Byte? = null): Function<LocalTime> = TimeInternal(expr, precision)

/**
 * Represents the base SQL function that returns the current date and time, as determined by the specified [columnType]
 * and fractional seconds [precision].
 */
sealed class CurrentTimestampBase<T>(private val precision: Byte?, columnType: IColumnType<T & Any>) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true ||
                (currentDialect !is SQLiteDialect && currentDialect !is SQLServerDialect) -> "CURRENT_TIMESTAMP${precision?.let { "($it)" }.orEmpty()}"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

/**
 * Represents an SQL function that returns the current date and time, as [LocalDateTime] with the specified fractional seconds [precision].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
open class CurrentDateTime(precision: Byte? = null) : CurrentTimestampBase<LocalDateTime>(precision, KotlinLocalDateTimeColumnType(precision)) {
    companion object : CurrentDateTime()
}

/**
 * Represents an SQL function that returns the current date and time, as [Instant] with the specified fractional seconds [precision].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
open class CurrentTimestamp(precision: Byte? = null) : CurrentTimestampBase<Instant>(precision, KotlinInstantColumnType(precision)) {
    companion object : CurrentTimestamp()
}

/**
 * Represents an SQL function that returns the current date and time with time zone, as [OffsetDateTime] with the specified fractional seconds [precision].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testTimestampWithTimeZoneDefaults
 */
open class CurrentTimestampWithTimeZone(precision: Byte? = null) : CurrentTimestampBase<OffsetDateTime>(precision, KotlinOffsetDateTimeColumnType(precision)) {
    companion object : CurrentTimestampWithTimeZone()
}

/**
 * Represents an SQL function that returns the current date, as [LocalDate].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
object CurrentDate : Function<LocalDate>(KotlinLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (currentDialect) {
            is MariaDBDialect -> "curdate()"
            is MysqlDialect -> "CURRENT_DATE"
            is SQLServerDialect -> "GETDATE()"
            else -> "CURRENT_DATE"
        }
    }
}

class YearInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.year(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the year field from a given date [expr]. */
@JvmName("LocalDateYearFunction")
fun <T : LocalDate?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)

/** Represents an SQL function that extracts the year field from a given datetime [expr]. */
@JvmName("LocalDateTimeYearFunction")
fun <T : LocalDateTime?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)

/** Represents an SQL function that extracts the year field from a given timestamp [expr]. */
@JvmName("InstantYearFunction")
fun <T : Instant?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)

/** Represents an SQL function that extracts the year field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeYearFunction")
fun <T : OffsetDateTime?> Year(expr: Expression<T>): Function<Int> = YearInternal(expr)

internal class MonthInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.month(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the month field from a given date [expr]. */
@JvmName("LocalDateMonthFunction")
fun <T : LocalDate?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)

/** Represents an SQL function that extracts the month field from a given datetime [expr]. */
@JvmName("LocalDateTimeMonthFunction")
fun <T : LocalDateTime?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)

/** Represents an SQL function that extracts the month field from a given timestamp [expr]. */
@JvmName("InstantMonthFunction")
fun <T : Instant?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)

/** Represents an SQL function that extracts the month field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeMonthFunction")
fun <T : OffsetDateTime?> Month(expr: Expression<T>): Function<Int> = MonthInternal(expr)

internal class DayInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.day(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the day field from a given date [expr]. */
@JvmName("LocalDateDayFunction")
fun <T : LocalDate?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)

/** Represents an SQL function that extracts the day field from a given datetime [expr]. */
@JvmName("LocalDateTimeDayFunction")
fun <T : LocalDateTime?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)

/** Represents an SQL function that extracts the day field from a given timestamp [expr]. */
@JvmName("InstantDayFunction")
fun <T : Instant?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)

/** Represents an SQL function that extracts the day field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeDayFunction")
fun <T : OffsetDateTime?> Day(expr: Expression<T>): Function<Int> = DayInternal(expr)

internal class HourInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.hour(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the hour field from a given date [expr]. */
@JvmName("LocalDateHourFunction")
fun <T : LocalDate?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)

/** Represents an SQL function that extracts the hour field from a given datetime [expr]. */
@JvmName("LocalDateTimeHourFunction")
fun <T : LocalDateTime?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)

/** Represents an SQL function that extracts the hour field from a given timestamp [expr]. */
@JvmName("InstantHourFunction")
fun <T : Instant?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)

/** Represents an SQL function that extracts the hour field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeHourFunction")
fun <T : OffsetDateTime?> Hour(expr: Expression<T>): Function<Int> = HourInternal(expr)

internal class MinuteInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.minute(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the minute field from a given date [expr]. */
@JvmName("LocalDateMinuteFunction")
fun <T : LocalDate?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)

/** Represents an SQL function that extracts the minute field from a given datetime [expr]. */
@JvmName("LocalDateTimeMinuteFunction")
fun <T : LocalDateTime?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)

/** Represents an SQL function that extracts the minute field from a given timestamp [expr]. */
@JvmName("InstantMinuteFunction")
fun <T : Instant?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)

/** Represents an SQL function that extracts the minute field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeMinuteFunction")
fun <T : OffsetDateTime?> Minute(expr: Expression<T>): Function<Int> = MinuteInternal(expr)

internal class SecondInternal(val expr: Expression<*>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.second(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the second field from a given date [expr]. */
@JvmName("LocalDateSecondFunction")
fun <T : LocalDate?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)

/** Represents an SQL function that extracts the second field from a given datetime [expr]. */
@JvmName("LocalDateTimeSecondFunction")
fun <T : LocalDateTime?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)

/** Represents an SQL function that extracts the second field from a given timestamp [expr]. */
@JvmName("InstantSecondFunction")
fun <T : Instant?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)

/** Represents an SQL function that extracts the second field from a given timestampWithTimeZone [expr]. */
@JvmName("OffsetDateTimeSecondFunction")
fun <T : OffsetDateTime?> Second(expr: Expression<T>): Function<Int> = SecondInternal(expr)

// Extension functions

/** Returns the date from this date expression. */
@JvmName("LocalDateDateExt")
fun <T : LocalDate?> Expression<T>.date() = Date(this)

/** Returns the date from this datetime expression. */
@JvmName("LocalDateTimeDateExt")
fun <T : LocalDateTime?> Expression<T>.date() = Date(this)

/** Returns the date from this timestamp expression. */
@JvmName("InstantDateExt")
fun <T : Instant?> Expression<T>.date() = Date(this)

/** Returns the date from this timestampWithTimeZone expression. */
@JvmName("OffsetDateTimeDateExt")
fun <T : OffsetDateTime?> Expression<T>.date() = Date(this)

/** Returns the time from this date expression. */
@JvmName("LocalDateTimeExt")
fun <T : LocalDate?> Expression<T>.time() = Time(this)

/** Returns the time, with fractional seconds [precision], from this datetime expression . */
@JvmName("LocalDateTimeTimeExt")
fun <T : LocalDateTime?> Expression<T>.time(precision: Byte? = null) = Time(this, precision)

/** Returns the time, with fractional seconds [precision], from this timestamp expression. */
@JvmName("InstantTimeExt")
fun <T : Instant?> Expression<T>.time(precision: Byte? = null) = Time(this, precision)

/** Returns the time, with fractional seconds [precision], from this timestampWithTimeZone expression. */
@JvmName("OffsetDateTimeTimeExt")
fun <T : OffsetDateTime?> Expression<T>.time(precision: Byte? = null) = Time(this, precision)

/** Returns the year from this date expression, as an integer. */
@JvmName("LocalDateYearExt")
fun <T : LocalDate?> Expression<T>.year() = Year(this)

/** Returns the year from this datetime expression, as an integer. */
@JvmName("LocalDateTimeYearExt")
fun <T : LocalDateTime?> Expression<T>.year() = Year(this)

/** Returns the year from this timestamp expression, as an integer. */
@JvmName("InstantYearExt")
fun <T : Instant?> Expression<T>.year() = Year(this)

/** Returns the year from this timestampWithTimeZone expression, as an integer. */
@JvmName("OffsetDateTimeYearExt")
fun <T : OffsetDateTime?> Expression<T>.year() = Year(this)

/** Returns the month from this date expression, as an integer between 1 and 12 inclusive. */
@JvmName("LocalDateMonthExt")
fun <T : LocalDate?> Expression<T>.month() = Month(this)

/** Returns the month from this datetime expression, as an integer between 1 and 12 inclusive. */
@JvmName("LocalDateTimeMonthExt")
fun <T : LocalDateTime?> Expression<T>.month() = Month(this)

/** Returns the month from this timestamp expression, as an integer between 1 and 12 inclusive. */
@JvmName("InstantMonthExt")
fun <T : Instant?> Expression<T>.month() = Month(this)

/** Returns the month from this timestampWithTimeZone expression, as an integer between 1 and 12 inclusive. */
@JvmName("OffsetDateTimeMonthExt")
fun <T : OffsetDateTime?> Expression<T>.month() = Month(this)

/** Returns the day from this date expression, as an integer between 1 and 31 inclusive. */
@JvmName("LocalDateDayExt")
fun <T : LocalDate?> Expression<T>.day() = Day(this)

/** Returns the day from this datetime expression, as an integer between 1 and 31 inclusive. */
@JvmName("LocalDateTimeDayExt")
fun <T : LocalDateTime?> Expression<T>.day() = Day(this)

/** Returns the day from this timestamp expression, as an integer between 1 and 31 inclusive. */
@JvmName("InstantDayExt")
fun <T : Instant?> Expression<T>.day() = Day(this)

/** Returns the day from this timestampWithTimeZone expression, as an integer between 1 and 31 inclusive. */
@JvmName("OffsetDateTimeDayExt")
fun <T : OffsetDateTime?> Expression<T>.day() = Day(this)

/** Returns the hour from this date expression, as an integer between 0 and 23 inclusive. */
@JvmName("LocalDateHourExt")
fun <T : LocalDate?> Expression<T>.hour() = Hour(this)

/** Returns the hour from this datetime expression, as an integer between 0 and 23 inclusive. */
@JvmName("LocalDateTimeHourExt")
fun <T : LocalDateTime?> Expression<T>.hour() = Hour(this)

/** Returns the hour from this timestamp expression, as an integer between 0 and 23 inclusive. */
@JvmName("InstantHourExt")
fun <T : Instant?> Expression<T>.hour() = Hour(this)

/** Returns the hour from this timestampWithTimeZone expression, as an integer between 0 and 23 inclusive. */
@JvmName("OffsetDateTimeHourExt")
fun <T : OffsetDateTime?> Expression<T>.hour() = Hour(this)

/** Returns the minute from this date expression, as an integer between 0 and 59 inclusive. */
@JvmName("LocalDateMinuteExt")
fun <T : LocalDate?> Expression<T>.minute() = Minute(this)

/** Returns the minute from this datetime expression, as an integer between 0 and 59 inclusive. */
@JvmName("LocalDateTimeMinuteExt")
fun <T : LocalDateTime?> Expression<T>.minute() = Minute(this)

/** Returns the minute from this timestamp expression, as an integer between 0 and 59 inclusive. */
@JvmName("InstantMinuteExt")
fun <T : Instant?> Expression<T>.minute() = Minute(this)

/** Returns the minute from this timestampWithTimeZone expression, as an integer between 0 and 59 inclusive. */
@JvmName("OffsetDateTimeMinuteExt")
fun <T : OffsetDateTime?> Expression<T>.minute() = Minute(this)

/** Returns the second from this date expression, as an integer between 0 and 59 inclusive. */
@JvmName("LocalDateSecondExt")
fun <T : LocalDate?> Expression<T>.second() = Second(this)

/** Returns the second from this datetime expression, as an integer between 0 and 59 inclusive. */
@JvmName("LocalDateTimeSecondExt")
fun <T : LocalDateTime?> Expression<T>.second() = Second(this)

/** Returns the second from this timestamp expression, as an integer between 0 and 59 inclusive. */
@JvmName("InstantSecondExt")
fun <T : Instant?> Expression<T>.second() = Second(this)

/** Returns the second from this timestampWithTimeZone expression, as an integer between 0 and 59 inclusive. */
@JvmName("OffsetDateTimeSecondExt")
fun <T : OffsetDateTime?> Expression<T>.second() = Second(this)

/** Returns the specified [value] as a date query parameter. */
fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, KotlinLocalDateColumnType.INSTANCE)

/** Returns the specified [value] as a time query parameter with fractional seconds [precision]. */
fun timeParam(value: LocalTime, precision: Byte? = null): Expression<LocalTime> = QueryParameter(value, KotlinLocalTimeColumnType(precision))

/** Returns the specified [value] as a date with time query parameter with fractional seconds [precision]. */
fun dateTimeParam(value: LocalDateTime, precision: Byte? = null): Expression<LocalDateTime> = QueryParameter(value, KotlinLocalDateTimeColumnType(precision))

/** Returns the specified [value] as a timestamp query parameter with fractional seconds [precision]. */
fun timestampParam(value: Instant, precision: Byte? = null): Expression<Instant> = QueryParameter(value, KotlinInstantColumnType(precision))

/** Returns the specified [value] as a date with time and time zone query parameter with fractional seconds [precision]. */
fun timestampWithTimeZoneParam(value: OffsetDateTime, precision: Byte? = null): Expression<OffsetDateTime> =
    QueryParameter(value, KotlinOffsetDateTimeColumnType(precision))

/** Returns the specified [value] as a duration query parameter. */
fun durationParam(value: Duration): Expression<Duration> = QueryParameter(value, KotlinDurationColumnType.INSTANCE)

/** Returns the specified [value] as a date literal. */
fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(KotlinLocalDateColumnType.INSTANCE, value)

/** Returns the specified [value] as a time literal with fractional seconds [precision]. */
fun timeLiteral(value: LocalTime, precision: Byte? = null): LiteralOp<LocalTime> = LiteralOp(KotlinLocalTimeColumnType(precision), value)

/** Returns the specified [value] as a date with time literal with fractional seconds [precision]. */
fun dateTimeLiteral(value: LocalDateTime, precision: Byte? = null): LiteralOp<LocalDateTime> = LiteralOp(KotlinLocalDateTimeColumnType(precision), value)

/** Returns the specified [value] as a timestamp literal with fractional seconds [precision]. */
fun timestampLiteral(value: Instant, precision: Byte? = null): LiteralOp<Instant> = LiteralOp(KotlinInstantColumnType(precision), value)

/** Returns the specified [value] as a date with time and time zone literal with fractional seconds [precision]. */
fun timestampWithTimeZoneLiteral(value: OffsetDateTime, precision: Byte? = null): LiteralOp<OffsetDateTime> =
    LiteralOp(KotlinOffsetDateTimeColumnType(precision), value)

/** Returns the specified [value] as a duration literal. */
fun durationLiteral(value: Duration): LiteralOp<Duration> = LiteralOp(KotlinDurationColumnType.INSTANCE, value)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a date only,
 * and passing [params] as its arguments.
 */
fun CustomDateFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDate?> =
    CustomFunction(functionName, KotlinLocalDateColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a time only, with fractional seconds
 * [precision], and passing [params] as its arguments.
 */
fun CustomTimeFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, KotlinLocalTimeColumnType(precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with fractional
 * seconds [precision], and passing [params] as its arguments.
 */
fun CustomDateTimeFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>): CustomFunction<LocalDateTime?> =
    CustomFunction(functionName, KotlinLocalDateTimeColumnType(precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a timestamp with fractional seconds
 * [precision], and passing [params] as its arguments.
 */
fun CustomTimeStampFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>): CustomFunction<Instant?> =
    CustomFunction(functionName, KotlinInstantColumnType(precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with time zone
 * with fractional seconds [precision], and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomTimestampWithTimeZoneFunction(
    functionName: String,
    precision: Byte? = null,
    vararg params: Expression<*>
): CustomFunction<OffsetDateTime?> = CustomFunction(functionName, KotlinOffsetDateTimeColumnType(precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a duration,
 * and passing [params] as its arguments.
 */
fun CustomDurationFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Duration?> =
    CustomFunction(functionName, KotlinDurationColumnType.INSTANCE, *params)
