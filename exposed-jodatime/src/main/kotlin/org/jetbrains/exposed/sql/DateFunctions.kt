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

class Year<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(YEAR FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(YEAR FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(YEAR FROM ", expr, ")")
            is SQLServerDialect -> append("YEAR(", expr, ")")
            is MariaDBDialect -> append("YEAR(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%Y',", expr, ")")
            is H2Dialect -> append("YEAR(", expr, ")")
            else -> append("YEAR(", expr, ")")
        }
    }
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(MONTH FROM ", expr, ")")
            is SQLServerDialect -> append("MONTH(", expr, ")")
            is MariaDBDialect -> append("MONTH(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%m',", expr, ")")
            is H2Dialect -> append("MONTH(", expr, ")")
            else -> append("MONTH(", expr, ")")
        }
    }
}

class Day<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(DAY FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(DAY FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(DAY FROM ", expr, ")")
            is SQLServerDialect -> append("DAY(", expr, ")")
            is MariaDBDialect -> append("DAY(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%d',", expr, ")")
            is H2Dialect -> append("DAY(", expr, ")")
            else -> append("DAY(", expr, ")")
        }
    }
}

class Hour<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(HOUR FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(HOUR FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(HOUR FROM ", expr, ")")
            is SQLServerDialect -> append("HOUR(", expr, ")")
            is MariaDBDialect -> append("HOUR(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%H',", expr, ")")
            is H2Dialect -> append("HOUR(", expr, ")")
            else -> append("HOUR(", expr, ")")
        }
    }
}

class Minute<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(MINUTE FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(MINUTE FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(MINUTE FROM ", expr, ")")
            is SQLServerDialect -> append("MINUTE(", expr, ")")
            is MariaDBDialect -> append("MINUTE(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%M',", expr, ")")
            is H2Dialect -> append("MINUTE(", expr, ")")
            else -> append("MINUTE(", expr, ")")
        }
    }
}

class Second<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialect) {
            is PostgreSQLDialect -> append("EXTRACT(SECOND FROM ", expr, ")")
            is OracleDialect -> append("EXTRACT(SECOND FROM ", expr, ")")
            is MysqlDialect -> append("EXTRACT(SECOND FROM ", expr, ")")
            is SQLServerDialect -> append("SECOND(", expr, ")")
            is MariaDBDialect -> append("SECOND(", expr, ")")
            is SQLiteDialect -> append("STRFTIME('%S',", expr, ")")
            is H2Dialect -> append("SECOND(", expr, ")")
            else -> append("SECOND(", expr, ")")
        }
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
