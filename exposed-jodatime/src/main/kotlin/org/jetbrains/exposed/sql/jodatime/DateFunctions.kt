package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
import org.joda.time.LocalTime

/** Represents an SQL function that extracts the date part from a given datetime [expr]. */
class Date<T : DateTime?>(val expr: Expression<T>) : Function<DateTime>(DateColumnType(false)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.date(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the time part from a given datetime [expr], with fractional seconds [precision]. */
class Time<T : DateTime?>(val expr: Expression<T>, precision: Byte? = null) : Function<LocalTime>(LocalTimeColumnType(precision)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer,
            H2Dialect.H2CompatibilityMode.PostgreSQL,
            H2Dialect.H2CompatibilityMode.Oracle ->
                (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.time(expr, queryBuilder)
    }
}

/**
 * Represents an SQL function that returns the current date and time, as [DateTime] with the specified fractional seconds [precision].
 *
 * @sample org.jetbrains.exposed.JodaTimeDefaultsTest.testDefaultExpressions02
 */
open class CurrentDateTime(private val precision: Byte? = null) : Function<DateTime>(DateColumnType(true, precision)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true ||
                (currentDialect !is SQLiteDialect && currentDialect !is SQLServerDialect) -> "CURRENT_TIMESTAMP${precision?.let { "($it)" }.orEmpty()}"
            else -> "CURRENT_TIMESTAMP"
        }
    }

    companion object : CurrentDateTime()
}

/**
 * Represents an SQL function that returns the current date, as [DateTime].
 *
 * @sample org.jetbrains.exposed.JodaTimeDefaultsTest.testDefaultExpressions02
 */
object CurrentDate : Function<DateTime>(DateColumnType(false)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (currentDialect) {
            is MariaDBDialect -> "curdate()"
            is MysqlDialect -> "CURRENT_DATE()"
            is SQLServerDialect -> "GETDATE()"
            else -> "CURRENT_DATE"
        }
    }
}

/** Represents an SQL function that extracts the year field from a given datetime [expr]. */
class Year<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.year(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the month field from a given datetime [expr]. */
class Month<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.month(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the day field from a given datetime [expr]. */
class Day<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.day(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the hour field from a given datetime [expr]. */
class Hour<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.hour(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the minute field from a given datetime [expr]. */
class Minute<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.minute(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the second field from a given datetime [expr]. */
class Second<T : DateTime?>(val expr: Expression<T>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val dialect = currentDialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.SQLServer -> (dialect as H2Dialect).originalFunctionProvider
            else -> dialect.functionProvider
        }
        functionProvider.second(expr, queryBuilder)
    }
}

/** Returns the date from this datetime expression. */
fun <T : DateTime?> Expression<T>.date() = Date(this)

/** Returns the time, with fractional seconds [precision], from this datetime expression. */
fun <T : DateTime?> Expression<T>.time(precision: Byte? = null) = Time(this, precision)

/** Returns the year from this datetime expression, as an integer. */
fun <T : DateTime?> Expression<T>.year() = Year(this)

/** Returns the month from this datetime expression, as an integer between 1 and 12 inclusive. */
fun <T : DateTime?> Expression<T>.month() = Month(this)

/** Returns the day from this datetime expression, as an integer between 1 and 31 inclusive. */
fun <T : DateTime?> Expression<T>.day() = Day(this)

/** Returns the hour from this datetime expression, as an integer between 0 and 23 inclusive. */
fun <T : DateTime?> Expression<T>.hour() = Hour(this)

/** Returns the minute from this datetime expression, as an integer between 0 and 59 inclusive. */
fun <T : DateTime?> Expression<T>.minute() = Minute(this)

/** Returns the second from this datetime expression, as an integer between 0 and 59 inclusive. */
fun <T : DateTime?> Expression<T>.second() = Second(this)

/** Returns the specified [value] as a date query parameter. */
fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))

/** Returns the specified [value] as a date with time query parameter with fractional seconds [precision]. */
fun dateTimeParam(value: DateTime, precision: Byte? = null): Expression<DateTime> = QueryParameter(value, DateColumnType(true, precision))

/** Returns the specified [value] as a time query parameter with fractional seconds [precision]. */
fun timeParam(value: LocalTime, precision: Byte? = null): Expression<LocalTime> = QueryParameter(value, LocalTimeColumnType(precision))

/** Returns the specified [value] as a date with time and time zone query parameter with fractional seconds [precision]. */
fun timestampWithTimeZoneParam(value: DateTime, precision: Byte? = null): Expression<DateTime> =
    QueryParameter(value, DateTimeWithTimeZoneColumnType(precision))

/** Returns the specified [value] as a date literal. */
fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(false), value)

/** Returns the specified [value] as a date with time literal with fractional seconds [precision]. */
fun dateTimeLiteral(value: DateTime, precision: Byte? = null): LiteralOp<DateTime> = LiteralOp(DateColumnType(true, precision), value)

/** Returns the specified [value] as a time literal with fractional seconds [precision]. */
fun timeLiteral(value: LocalTime, precision: Byte? = null): LiteralOp<LocalTime> = LiteralOp(LocalTimeColumnType(precision), value)

/** Returns the specified [value] as a date with time and time zone literal with fractional seconds [precision]. */
fun timestampWithTimeZoneLiteral(value: DateTime, precision: Byte? = null): LiteralOp<DateTime> =
    LiteralOp(DateTimeWithTimeZoneColumnType(precision), value)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with fractional
 * seconds [precision], and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomDateTimeFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, DateColumnType(true, precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a date only,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomDateFunction(functionName: String, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, DateColumnType(false), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a time only, with fractional seconds
 * [precision] and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomTimeFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, LocalTimeColumnType(precision), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with time zone
 * with fractional seconds [precision], and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomTimestampWithTimeZoneFunction(functionName: String, precision: Byte? = null, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, DateTimeWithTimeZoneColumnType(precision), *params)
