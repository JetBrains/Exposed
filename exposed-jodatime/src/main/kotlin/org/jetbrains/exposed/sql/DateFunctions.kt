package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime

class Date<T:DateTime?>(val expr: Expression<T>): Function<DateTime>(DateColumnType(false)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "DATE(${expr.toSQL(queryBuilder)})"
}

class CurrentDateTime : Function<DateTime>(DateColumnType(false)) {
    override fun toSQL(queryBuilder: QueryBuilder) = when {
        (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
        else -> "CURRENT_TIMESTAMP"
    }
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MONTH(${expr.toSQL(queryBuilder)})"
}

fun <T: DateTime?> Expression<T>.date() = Date(this)

fun <T: DateTime?> Expression<T>.month() = Month(this)


fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))
fun dateTimeParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(true))

fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(false), value)
fun dateTimeLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(true), value)

fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<DateTime?>(functionName, DateColumnType(true), *params)
