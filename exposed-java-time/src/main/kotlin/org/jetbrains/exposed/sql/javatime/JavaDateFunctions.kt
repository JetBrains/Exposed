package org.jetbrains.exposed.sql.javatime

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.Temporal

class Date<T : Temporal?>(val expr: Expression<T>) : Function<LocalDate>(JavaLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("DATE(", expr, ")") }
}

class Time<T : Temporal?>(val expr: Expression<T>) : Function<LocalTime>(JavaLocalTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("Time(", expr, ")") }
}

object CurrentDateTime : Function<LocalDateTime>(JavaLocalDateTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }

    @Deprecated(
        message = "This class is now a singleton, no need for its constructor call; " +
            "this method is provided for backward-compatibility only, and will be removed in future releases",
        replaceWith = ReplaceWith("this"),
        level = DeprecationLevel.ERROR,
    )
    operator fun invoke() = this
}

object CurrentDate : Function<LocalDate>(JavaLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (currentDialect) {
            is MysqlDialect -> "CURRENT_DATE()"
            is SQLServerDialect -> "GETDATE()"
            else -> "CURRENT_DATE"
        }
    }
}

class CurrentTimestamp<T : Temporal> : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

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

fun <T : Temporal?> Expression<T>.date(): Date<T> = Date(this)

fun <T : Temporal?> Expression<T>.year(): Year<T> = Year(this)
fun <T : Temporal?> Expression<T>.month(): Month<T> = Month(this)
fun <T : Temporal?> Expression<T>.day(): Day<T> = Day(this)
fun <T : Temporal?> Expression<T>.hour(): Hour<T> = Hour(this)
fun <T : Temporal?> Expression<T>.minute(): Minute<T> = Minute(this)
fun <T : Temporal?> Expression<T>.second(): Second<T> = Second(this)

fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, JavaLocalDateColumnType.INSTANCE)
fun timeParam(value: LocalTime): Expression<LocalTime> = QueryParameter(value, JavaLocalTimeColumnType.INSTANCE)
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> =
    QueryParameter(value, JavaLocalDateTimeColumnType.INSTANCE)

fun timestampParam(value: Instant): Expression<Instant> = QueryParameter(value, JavaInstantColumnType.INSTANCE)
fun durationParam(value: Duration): Expression<Duration> = QueryParameter(value, JavaDurationColumnType.INSTANCE)

fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(JavaLocalDateColumnType.INSTANCE, value)
fun timeLiteral(value: LocalTime): LiteralOp<LocalTime> = LiteralOp(JavaLocalTimeColumnType.INSTANCE, value)
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(JavaLocalDateTimeColumnType.INSTANCE, value)

fun timestampLiteral(value: Instant): LiteralOp<Instant> = LiteralOp(JavaInstantColumnType.INSTANCE, value)
fun durationLiteral(value: Duration): LiteralOp<Duration> = LiteralOp(JavaDurationColumnType.INSTANCE, value)

@Suppress("FunctionName")
fun CustomDateFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDate?> =
    CustomFunction(functionName, JavaLocalDateColumnType.INSTANCE, *params)

@Suppress("FunctionName")
fun CustomTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalTime?> =
    CustomFunction(functionName, JavaLocalTimeColumnType.INSTANCE, *params)

@Suppress("FunctionName")
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>): CustomFunction<LocalDateTime?> =
    CustomFunction(functionName, JavaLocalDateTimeColumnType.INSTANCE, *params)

@Suppress("FunctionName")
fun CustomTimeStampFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Instant?> =
    CustomFunction(functionName, JavaInstantColumnType.INSTANCE, *params)

@Suppress("FunctionName")
fun CustomDurationFunction(functionName: String, vararg params: Expression<*>): CustomFunction<Duration?> =
    CustomFunction(functionName, JavaDurationColumnType.INSTANCE, *params)
