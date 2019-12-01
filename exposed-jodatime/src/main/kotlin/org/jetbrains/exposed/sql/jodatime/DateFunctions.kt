package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime

class Date<T:DateTime?>(val expr: Expression<T>): Function<DateTime>(DateColumnType(false)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("DATE(", expr,")") }
}

class CurrentDateTime : Function<DateTime>(DateColumnType(false)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

class Year<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.year(expr, queryBuilder)
    }
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.month(expr, queryBuilder)
    }
}

class Day<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.day(expr, queryBuilder)
    }
}

class Hour<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.hour(expr, queryBuilder)
    }
}

class Minute<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.minute(expr, queryBuilder)
    }
}

class Second<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        currentDialect.functionProvider.second(expr, queryBuilder)
    }
}

fun <T: DateTime?> Expression<T>.date() = Date(this)

fun <T: DateTime?> Expression<T>.year() = Year(this)
fun <T: DateTime?> Expression<T>.month() = Month(this)
fun <T: DateTime?> Expression<T>.day() = Day(this)
fun <T: DateTime?> Expression<T>.hour() = Hour(this)
fun <T: DateTime?> Expression<T>.minute() = Minute(this)
fun <T: DateTime?> Expression<T>.second() = Second(this)


fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))
fun dateTimeParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(true))

fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(false), value)
fun dateTimeLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(true), value)

fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<DateTime?>(functionName, DateColumnType(true), *params)
