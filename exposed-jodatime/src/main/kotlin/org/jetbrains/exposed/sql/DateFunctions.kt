package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
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

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is SQLServerDialect -> append("MONTH(", expr, ")")
            is MariaDBDialect -> append("MONTH(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%m',", expr, ")")
            is H2Dialect -> append("MONTH(", expr, ")")
            else -> append("MONTH(", expr, ")")
        }
    }
}

fun <T: DateTime?> Expression<T>.date() = Date(this)

fun <T: DateTime?> Expression<T>.month() = Month(this)


fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))
fun dateTimeParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(true))

fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(false), value)
fun dateTimeLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(true), value)

fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<DateTime?>(functionName, DateColumnType(true), *params)
