package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
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

class Month<T:Temporal?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MONTH(", expr,")") }
}

fun <T: Temporal?> Expression<T>.date() = Date(this)

fun <T: Temporal?> Expression<T>.month() = Month(this)


fun dateParam(value: LocalDate): Expression<LocalDate> = QueryParameter(value, JavaLocalDateColumnType.INSTANCE)
fun dateTimeParam(value: LocalDateTime): Expression<LocalDateTime> = QueryParameter(value, JavaLocalDateTimeColumnType.INSTANCE)

fun dateLiteral(value: LocalDate): LiteralOp<LocalDate> = LiteralOp(JavaLocalDateColumnType.INSTANCE, value)
fun dateTimeLiteral(value: LocalDateTime): LiteralOp<LocalDateTime> = LiteralOp(JavaLocalDateTimeColumnType.INSTANCE, value)

fun CustomDateFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDate?>(functionName, JavaLocalDateColumnType.INSTANCE, *params)
fun CustomDateTimeFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<LocalDateTime?>(functionName, JavaLocalDateTimeColumnType.INSTANCE, *params)
