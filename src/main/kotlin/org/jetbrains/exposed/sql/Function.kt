package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.*

abstract class Function<T> : ExpressionWithColumnType<T>()

class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>() {
    override fun toSQL(queryBuilder: QueryBuilder): String =
            "COUNT(${if (distinct) "DISTINCT " else ""}${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = IntegerColumnType()
}

class Date<T:DateTime?>(val expr: Expression<T>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "DATE(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DateColumnType(false)
}

class CurrentDateTime : Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "CURRENT_TIMESTAMP"
    override val columnType: IColumnType = DateColumnType(false)
}

class Month<T:DateTime?>(val expr: Expression<T>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MONTH(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DateColumnType(false)
}

class LowerCase<T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "LOWER(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = VarCharColumnType()
}

class UpperCase<T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "UPPER(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = VarCharColumnType()
}

class Min<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MIN(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = _columnType
}

class Max<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "MAX(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = _columnType
}

class Avg<T:Comparable<T>, in S:T?>(val expr: Expression<in S>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "AVG(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "STDDEV_POP(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "STDDEV_SAMP(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarPop<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "VAR_POP(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarSamp<T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "VAR_SAMP(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class Sum<T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "SUM(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = _columnType
}

class Coalesce<out T, S:T?, R:T>(private val expr: ExpressionWithColumnType<S>, private val alternate: ExpressionWithColumnType<out T>): Function<R>() {
    override fun toSQL(queryBuilder: QueryBuilder): String =
            "COALESCE(${expr.toSQL(queryBuilder)}, ${alternate.toSQL(queryBuilder)})"

    override val columnType: IColumnType = alternate.columnType
}

class Substring<T:String?>(private val expr: Expression<T>, private val start: ExpressionWithColumnType<Int>, val length: ExpressionWithColumnType<Int>): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.substring(expr, start, length, queryBuilder)

    override val columnType: IColumnType = VarCharColumnType()
}


class Random(val seed: Int? = null) : Function<BigDecimal>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.random(seed)

    override val columnType: IColumnType = DecimalColumnType(38, 20)
}

class Cast<T>(val expr: Expression<*>, override val columnType: IColumnType) : Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
}

class Trim<T:String?>(val expr: Expression<T>): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "TRIM(${expr.toSQL(queryBuilder)})"

    override val columnType: IColumnType = VarCharColumnType()
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

class GroupConcat(val expr: Column<*>, val separator: String?, val distinct: Boolean, vararg val orderBy: Pair<Expression<*>,Boolean>): Function<String?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = buildString {
        append("GROUP_CONCAT(")
        if (distinct)
            append("DISTINCT ")
        append(expr.toSQL(queryBuilder))
        orderBy.forEach {
            append(it.first.toSQL(queryBuilder))
            append(" ")
            if (it.second) {
                append("ASC")
            } else {
                append("DESC")
            }
        }
        separator?.let {
            append(" SEPARATOR '$separator'")
        }
        append(")")
    }

    override val columnType: IColumnType = VarCharColumnType()
}
