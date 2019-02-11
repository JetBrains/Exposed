package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.*

abstract class Function<T>(override val columnType: IColumnType) : ExpressionWithColumnType<T>()

class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>(IntegerColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String =
            "COUNT(${if (distinct) "DISTINCT " else ""}${expr.toSQL(queryBuilder)})"
}

class Date<T:DateTime?>(val expr: Expression<T>): Function<DateTime>(DateColumnType(false)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "DATE(${expr.toSQL(queryBuilder)})"
}

class CurrentDateTime : Function<DateTime>(DateColumnType(false)) {
    override fun toSQL(queryBuilder: QueryBuilder) = "CURRENT_TIMESTAMP"
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<Int>(IntegerColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MONTH(${expr.toSQL(queryBuilder)})"
}

class LowerCase<T: String?>(val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "LOWER(${expr.toSQL(queryBuilder)})"
}

class UpperCase<T: String?>(val expr: Expression<T>) : Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "UPPER(${expr.toSQL(queryBuilder)})"
}

class Min<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MIN(${expr.toSQL(queryBuilder)})"
}

class Max<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MAX(${expr.toSQL(queryBuilder)})"
}

class Avg<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "AVG(${expr.toSQL(queryBuilder)})"
}

class StdDevPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "STDDEV_POP(${expr.toSQL(queryBuilder)})"
}

class StdDevSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "STDDEV_SAMP(${expr.toSQL(queryBuilder)})"
}

class VarPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "VAR_POP(${expr.toSQL(queryBuilder)})"
}

class VarSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "VAR_SAMP(${expr.toSQL(queryBuilder)})"
}

class Sum<T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>(_columnType) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "SUM(${expr.toSQL(queryBuilder)})"
}

class Coalesce<out T, S:T?, R:T>(private val expr: ExpressionWithColumnType<S>,
                                 private val alternate: ExpressionWithColumnType<out T>): Function<R>(alternate.columnType) {
    override fun toSQL(queryBuilder: QueryBuilder): String =
            "COALESCE(${expr.toSQL(queryBuilder)}, ${alternate.toSQL(queryBuilder)})"
}

class Substring<T:String?>(private val expr: Expression<T>, private val start: Expression<Int>,
                           val length: Expression<Int>): Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.substring(expr, start, length, queryBuilder)
}


class Random(val seed: Int? = null) : Function<BigDecimal>(DecimalColumnType(38, 20)) {
    override fun toSQL(queryBuilder: QueryBuilder) = currentDialect.functionProvider.random(seed)
}

class Cast<T>(val expr: Expression<*>, columnType: IColumnType) : Function<T?>(columnType) {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
}

class Trim<T:String?>(val expr: Expression<T>): Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String = "TRIM(${expr.toSQL(queryBuilder)})"
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
    override fun toSQL(queryBuilder: QueryBuilder): String = buildString {
        append("CASE")
        if (caseWhen.value != null)
            append( " ${caseWhen.value.toSQL(queryBuilder)}")

        for ((first, second) in caseWhen.cases) {
            append(" WHEN ${first.toSQL(queryBuilder)} THEN ${second.toSQL(queryBuilder)}")
        }

        append(" ELSE ${elseResult.toSQL(queryBuilder)} END")
    }
}

class GroupConcat<T : String?>(
        val expr: Expression<T>,
        val separator: String?,
        val distinct: Boolean,
        vararg val orderBy: Pair<Expression<*>, SortOrder>
): Function<T>(VarCharColumnType()) {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.groupConcat(this, queryBuilder)
}
