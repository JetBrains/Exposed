package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
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

class Year<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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

class Month<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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

class Day<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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

class Hour<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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

class Minute<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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

class Second<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
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
