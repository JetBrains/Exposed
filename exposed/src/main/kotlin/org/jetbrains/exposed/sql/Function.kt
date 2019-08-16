package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.*

abstract class Function<T>(override val columnType: IColumnType) : ExpressionWithColumnType<T>()

class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder)= queryBuilder {
        +"COUNT("
        if (distinct) +"DISTINCT "
        +expr
        +")"
    }
}

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

open class CustomFunction<T>(val functionName: String, _columnType: IColumnType, vararg val expr: Expression<*>) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(functionName, '(')
        expr.toList().appendTo { +it }
        append(')')
    }
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MONTH(", expr,")") }
}

class LowerCase<T: String?>(val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("LOWER(", expr,")") }
}

class UpperCase<T: String?>(val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("UPPER(", expr,")") }
}

class Min<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MIN(", expr,")") }
}

class Max<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("MAX(", expr,")") }
}

class Avg<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder)= queryBuilder { append("AVG(", expr,")") }
}

class StdDevPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("STDDEV_POP(", expr,")") }
}

class StdDevSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("STDDEV_SAMP(", expr,")") }
}

class VarPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("VAR_POP(", expr,")") }
}

class VarSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("VAR_SAMP(", expr,")") }
}

class Sum<T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("SUM(", expr,")") }
}

class Coalesce<out T, S:T?, R:T>(private val expr: ExpressionWithColumnType<S>,
                                 private val alternate: ExpressionWithColumnType<out T>): Function<R>(alternate.columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("COALESCE(", expr, ", ", alternate, ")") }
}

class Substring<T:String?>(private val expr: Expression<T>, private val start: Expression<Int>,
                           val length: Expression<Int>): Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.substring(expr, start, length, queryBuilder)
    }
}


class Random(val seed: Int? = null) : Function<BigDecimal>(DecimalColumnType(38, 20)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { +currentDialect.functionProvider.random(seed) }
}

class Cast<T>(val expr: Expression<*>, columnType: IColumnType) : Function<T?>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
    }
}

class Trim<T:String?>(val expr: Expression<T>): Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("TRIM(", expr,")") }
}

class Case(val value: Expression<*>? = null) {
    fun<T> When (cond: Expression<Boolean>, result: Expression<T>) : CaseWhen<T> =
            CaseWhen<T>(value).When (cond, result)
}

class CaseWhen<T> (val value: Expression<*>?) {
    val cases: ArrayList<Pair<Expression<Boolean>, Expression<out T>>> =  ArrayList()

    @Suppress("UNCHECKED_CAST")
    fun <R:T> When (cond: Expression<Boolean>, result: Expression<R>) : CaseWhen<R> {
        cases.add( cond to result )
        return this as CaseWhen<R>
    }

    fun <R:T> Else(e: Expression<R>) : Expression<R> = CaseWhenElse(this, e)
}

class CaseWhenElse<T, R:T> (val caseWhen: CaseWhen<T>, val elseResult: Expression<R>) : Expression<R>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("CASE ")
        if (caseWhen.value != null)
            +caseWhen.value

        for ((first, second) in caseWhen.cases) {
            append(" WHEN ", first, " THEN ", second)
        }

        append(" ELSE ", elseResult, " END")
    }
}

class GroupConcat<T : String?>(
        val expr: Expression<T>,
        val separator: String?,
        val distinct: Boolean,
        vararg val orderBy: Pair<Expression<*>, SortOrder>
): Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.groupConcat(this, queryBuilder)
    }
}

class Concat<T: String?>(val separator: String, vararg val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.concat(separator, queryBuilder, *expr)
    }
}
