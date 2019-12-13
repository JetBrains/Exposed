package org.jetbrains.exposed.sql.`java-time`

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.Temporal

class Date<T:Temporal?>(val expr: Expression<T>): Function<LocalDate>(JavaLocalDateColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("DATE(", expr,")") }
}

class CurrentDateTime : Function<LocalDateTime>(JavaLocalDateTimeColumnType.INSTANCE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

class CurrentTimestamp<T: Temporal> : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

class Year<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.year(expr, queryBuilder)
    }
}

class Month<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.month(expr, queryBuilder)
    }
}

class Day<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.day(expr, queryBuilder)
    }
}

class Hour<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.hour(expr, queryBuilder)
    }
}

class Minute<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.minute(expr, queryBuilder)
    }
}

class Second<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.second(expr, queryBuilder)
    }
}

fun <T: Temporal?> Expression<T>.date() = Date(this)

fun <T: Temporal?> Expression<T>.year() = Year(this)
fun <T: Temporal?> Expression<T>.month() = Month(this)
fun <T: Temporal?> Expression<T>.day() = Day(this)
fun <T: Temporal?> Expression<T>.hour() = Hour(this)
fun <T: Temporal?> Expression<T>.minute() = Minute(this)
fun <T: Temporal?> Expression<T>.second() = Second(this)


fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, JavaLocalDateColumnType.INSTANCE)
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> = QueryParameter(value, JavaLocalDateTimeColumnType.INSTANCE)

fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(JavaLocalDateColumnType.INSTANCE, value)
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(JavaLocalDateTimeColumnType.INSTANCE, value)

fun CustomDateFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDate?>(functionName, JavaLocalDateColumnType.INSTANCE, *params)
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDateTime?>(functionName, JavaLocalDateTimeColumnType.INSTANCE, *params)
