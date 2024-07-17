package org.jetbrains.exposed.sql.javatime

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.Temporal

/** Represents an SQL function that extracts the date part from a given temporal [expr]. */
class Date<T : Temporal?>(val expr: Expression<T>) : Function<LocalDate>(JavaLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.date(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the time part from a given temporal [expr]. */
class Time<T : Temporal?>(val expr: Expression<T>) : Function<LocalTime>(JavaLocalTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer, H2Dialect.H2CompatibilityMode.PostgreSQL ->
                (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.time(expr, queryBuilder)
    }
}

/**
 * Represents the base SQL function that returns the current date and time, as determined by the specified [columnType].
 */
sealed class CurrentTimestampBase<T>(columnType: IColumnType<T & Any>) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

/**
 * Represents an SQL function that returns the current date, as [LocalDate].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
object CurrentDate : Function<LocalDate>(JavaLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (currentDialect) {
            is MariaDBDialect -> "curdate()"
            is MysqlDialect -> "CURRENT_DATE()"
            is SQLServerDialect -> "GETDATE()"
            else -> "CURRENT_DATE"
        }
    }
}

/**
 * Represents an SQL function that returns the current date and time, as [LocalDateTime].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
object CurrentDateTime : CurrentTimestampBase<LocalDateTime>(JavaLocalDateTimeColumnType.INSTANCE)

/**
 * Represents an SQL function that returns the current date and time, as [Instant].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testConsistentSchemeWithFunctionAsDefaultExpression
 */
object CurrentTimestamp : CurrentTimestampBase<Instant>(JavaInstantColumnType.INSTANCE)

/**
 * Represents an SQL function that returns the current date and time with time zone, as [OffsetDateTime].
 *
 * @sample org.jetbrains.exposed.DefaultsTest.testTimestampWithTimeZoneDefaults
 */
object CurrentTimestampWithTimeZone : CurrentTimestampBase<OffsetDateTime>(JavaOffsetDateTimeColumnType.INSTANCE)

/** Represents an SQL function that extracts the year field from a given temporal [expr]. */
class Year<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.year(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the month field from a given temporal [expr]. */
class Month<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.month(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the day field from a given temporal [expr]. */
class Day<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.day(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the hour field from a given temporal [expr]. */
class Hour<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.hour(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the minute field from a given temporal [expr]. */
class Minute<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.minute(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the second field from a given temporal [expr]. */
class Second<T : Temporal?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.second(expr, queryBuilder)
    }
}

/** Returns the date from this temporal expression. */
fun <T : Temporal?> Expression<T>.date(): Date<T> = Date(this)

/** Returns the time from this temporal expression. */
fun <T : Temporal?> Expression<T>.time(): Time<T> = Time(this)

/** Returns the year from this temporal expression, as an integer. */
fun <T : Temporal?> Expression<T>.year(): Year<T> = Year(this)

/** Returns the month from this temporal expression, as an integer between 1 and 12 inclusive. */
fun <T : Temporal?> Expression<T>.month(): Month<T> = Month(this)

/** Returns the day from this temporal expression, as an integer between 1 and 31 inclusive. */
fun <T : Temporal?> Expression<T>.day(): Day<T> = Day(this)

/** Returns the hour from this temporal expression, as an integer between 0 and 23 inclusive. */
fun <T : Temporal?> Expression<T>.hour(): Hour<T> = Hour(this)

/** Returns the minute from this temporal expression, as an integer between 0 and 59 inclusive. */
fun <T : Temporal?> Expression<T>.minute(): Minute<T> = Minute(this)

/** Returns the second from this temporal expression, as an integer between 0 and 59 inclusive. */
fun <T : Temporal?> Expression<T>.second(): Second<T> = Second(this)

/** Returns the specified [value] as a date query parameter. */
fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, JavaLocalDateColumnType.INSTANCE)

/** Returns the specified [value] as a time query parameter. */
fun timeParam(value: LocalTime): Expression<LocalTime> = QueryParameter(value, JavaLocalTimeColumnType.INSTANCE)

/** Returns the specified [value] as a date with time query parameter. */
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> =
    QueryParameter(value, JavaLocalDateTimeColumnType.INSTANCE)

/** Returns the specified [value] as a timestamp query parameter. */
fun timestampParam(value: Instant): Expression<Instant> = QueryParameter(value, JavaInstantColumnType.INSTANCE)

/** Returns the specified [value] as a date with time and time zone query parameter. */
fun timestampWithTimeZoneParam(value: OffsetDateTime): Expression<OffsetDateTime> =
    QueryParameter(value, JavaOffsetDateTimeColumnType.INSTANCE)

/** Returns the specified [value] as a duration query parameter. */
fun durationParam(value: Duration): Expression<Duration> = QueryParameter(value, JavaDurationColumnType.INSTANCE)

/** Returns the specified [value] as a date literal. */
fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(JavaLocalDateColumnType.INSTANCE, value)

/** Returns the specified [value] as a time literal. */
fun timeLiteral(value: LocalTime): LiteralOp<LocalTime> = LiteralOp(JavaLocalTimeColumnType.INSTANCE, value)

/** Returns the specified [value] as a date with time literal. */
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(JavaLocalDateTimeColumnType.INSTANCE, value)

/** Returns the specified [value] as a timestamp literal. */
fun timestampLiteral(value: Instant): LiteralOp<Instant> = LiteralOp(JavaInstantColumnType.INSTANCE, value)

/** Returns the specified [value] as a date with time and time zone literal. */
fun timestampWithTimeZoneLiteral(value: OffsetDateTime): LiteralOp<OffsetDateTime> =
    LiteralOp(JavaOffsetDateTimeColumnType.INSTANCE, value)

/** Returns the specified [value] as a duration literal. */
fun durationLiteral(value: Duration): LiteralOp<Duration> = LiteralOp(JavaDurationColumnType.INSTANCE, value)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a date only,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomDateFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDate?> =
    CustomFunction(functionName, JavaLocalDateColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a time only,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, JavaLocalTimeColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDateTime?> =
    CustomFunction(functionName, JavaLocalDateTimeColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a timestamp,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomTimeStampFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Instant?> =
    CustomFunction(functionName, JavaInstantColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with time zone,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomTimestampWithTimeZoneFunction(
    functionName: String,
    vararg params: Expression<*>
): CustomFunction<OffsetDateTime?> = CustomFunction(functionName, JavaOffsetDateTimeColumnType.INSTANCE, *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a duration,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionName")
fun CustomDurationFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Duration?> =
    CustomFunction(functionName, JavaDurationColumnType.INSTANCE, *params)
