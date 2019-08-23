package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.Temporal

class Date<T:Temporal?>(val expr: Expression<T>): Function<LocalDate>(LocalDateColumnType.INSTANSE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("DATE(", expr,")") }
}

class CurrentDateTime : Function<LocalDateTime>(LocalDateTimeColumnType.INSTANSE) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}

class Month<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MONTH(", expr,")") }
}

fun <T: Temporal?> Expression<T>.date() = Date(this)

fun <T: Temporal?> Expression<T>.month() = Month(this)


fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, LocalDateColumnType.INSTANSE)
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> = QueryParameter(value, LocalDateTimeColumnType.INSTANSE)

fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(LocalDateColumnType.INSTANSE, value)
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(LocalDateTimeColumnType.INSTANSE, value)

fun CustomDateFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDate?>(functionName, LocalDateColumnType.INSTANSE, *params)
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDateTime?>(functionName, LocalDateTimeColumnType.INSTANSE, *params)
