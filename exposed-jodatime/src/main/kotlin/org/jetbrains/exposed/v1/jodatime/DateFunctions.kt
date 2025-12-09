package org.jetbrains.exposed.v1.jodatime

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import org.joda.time.DateTime
import org.joda.time.LocalTime

/** Represents an SQL function that extracts the date part from a given datetime [expr]. */
class Date<T : DateTime?>(val expr: Expression<T>) : Function<DateTime>(JodaLocalDateColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.date(expr, queryBuilder)
    }
}

/** Represents an SQL function that extracts the time part from a given datetime [expr]. */
class Time<T : DateTime?>(val expr: Expression<T>) : Function<LocalTime>(JodaLocalTimeColumnType()) {
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
 * Represents an SQL function that returns the current date and time, as [DateTime]
 *
 * @sample org.jetbrains.exposed.v1.jodatime.JodaTimeDefaultsTest.testDefaultExpressions02
 */
object CurrentDateTime : Function<DateTime>(JodaLocalDateTimeColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

/**
 * Represents an SQL function that returns the current date, as [DateTime].
 *
 * @sample org.jetbrains.exposed.v1.jodatime.JodaTimeDefaultsTest.testDefaultExpressions02
 */
object CurrentDate : Function<DateTime>(JodaLocalDateColumnType()) {
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

/** Returns the time from this datetime expression. */
fun <T : DateTime?> Expression<T>.time() = Time(this)

/**
 * Returns the year from this datetime expression, as an integer.
 *
 * **Note:** Some JDBC drivers, like for MySQL, may return a `Date` type for this SQL function,
 * following the format `YYYY-01-01`. To avoid unexpected exceptions in this case, the MySQL connector property
 * `yearIsDateType` should be set to `false`. Please check the documentation.
 */
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
fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, JodaLocalDateColumnType())

/** Returns the specified [value] as a date with time query parameter. */
fun dateTimeParam(value: DateTime): Expression<DateTime> = QueryParameter(value, JodaLocalDateTimeColumnType())

/** Returns the specified [value] as a time query parameter. */
fun timeParam(value: LocalTime): Expression<LocalTime> = QueryParameter(value, JodaLocalTimeColumnType())

/** Returns the specified [value] as a date with time and time zone query parameter. */
fun timestampWithTimeZoneParam(value: DateTime): Expression<DateTime> =
    QueryParameter(value, DateTimeWithTimeZoneColumnType())

/** Returns the specified [value] as a date literal. */
fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(JodaLocalDateColumnType(), value)

/** Returns the specified [value] as a date with time literal. */
fun dateTimeLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(JodaLocalDateTimeColumnType(), value)

/** Returns the specified [value] as a time literal. */
fun timeLiteral(value: LocalTime): LiteralOp<LocalTime> = LiteralOp(JodaLocalTimeColumnType(), value)

/** Returns the specified [value] as a date with time and time zone literal. */
fun timestampWithTimeZoneLiteral(value: DateTime): LiteralOp<DateTime> =
    LiteralOp(DateTimeWithTimeZoneColumnType(), value)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, JodaLocalDateTimeColumnType(), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a date only,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomDateFunction(functionName: String, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, JodaLocalDateColumnType(), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a time only,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, JodaLocalTimeColumnType(), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns both a date and a time with time zone,
 * and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomTimestampWithTimeZoneFunction(functionName: String, vararg params: Expression<*>) =
    CustomFunction<DateTime?>(functionName, DateTimeWithTimeZoneColumnType(), *params)
