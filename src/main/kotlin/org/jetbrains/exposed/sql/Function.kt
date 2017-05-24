package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.*

abstract class Function<out T> : ExpressionWithColumnType<T>()

class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COUNT(${if (distinct) "DISTINCT " else ""}${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = IntegerColumnType()
}

class Date(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "DATE(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DateColumnType(time = false, withTimezone = false)
}

class CurrentDateTime : Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "CURRENT_TIMESTAMP"
    override val columnType: IColumnType = DateColumnType(time = true, withTimezone = false)
}

class Month(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MONTH(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DateColumnType(time = false, withTimezone = false)
}

class LowerCase<out T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "LOWER(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = StringColumnType()
}

class UpperCase<out T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "UPPER(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = StringColumnType()
}

class Min<out T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MIN(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = _columnType
}

class Max<out T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MAX(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = _columnType
}

class Avg<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "AVG(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevPop<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "STDDEV_POP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevSamp<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "STDDEV_SAMP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarPop<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "VAR_POP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarSamp<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "VAR_SAMP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class Sum<out T>(val expr: Expression<T>, _columnType: IColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "SUM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = _columnType
}

class Coalesce<out T>(val expr: ExpressionWithColumnType<T?>, val alternate: ExpressionWithColumnType<out T>): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COALESCE(${expr.toSQL(queryBuilder)}, ${alternate.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = alternate.columnType
}

class Substring(val expr: Expression<String?>, val start: ExpressionWithColumnType<Int>, val length: ExpressionWithColumnType<Int>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.substring(expr, start, length, queryBuilder)

    override val columnType: IColumnType = StringColumnType()
}


class Random(val seed: Int? = null) : Function<BigDecimal>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.random(seed)

    override val columnType: IColumnType = DecimalColumnType(38, 20)
}

class Cast<out T>(val expr: Expression<*>, override val columnType: IColumnType) : Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String
            = currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
}

class Trim(val expr: Expression<*>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "TRIM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: IColumnType = StringColumnType()
}

class Case(val value: Expression<*>? = null) {
    fun<T> When (cond: Expression<Boolean>, result: Expression<T>) : CaseWhen<T> {
        return CaseWhen<T>(value).When (cond, result)
    }
}

class CaseWhen<T> (val value: Expression<*>?) {
    val cases: ArrayList<Pair<Expression<Boolean>, Expression<T>>> =  ArrayList()

    fun When (cond: Expression<Boolean>, result: Expression<T>) : CaseWhen<T> {
        cases.add( cond to result )
        return this
    }

    fun Else(e: Expression<T>) : Expression<T> {
        return CaseWhenElse(this, e)
    }
}

class CaseWhenElse<T> (val caseWhen: CaseWhen<T>, val elseResult: Expression<T>) : Expression<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        val sb = StringBuilder()
        sb.append("CASE")
        if (caseWhen.value != null)
            sb.append( " ${caseWhen.value.toSQL(queryBuilder)}")

        for (whenPair in caseWhen.cases) {
            sb.append(" WHEN ${whenPair.first.toSQL(queryBuilder)} THEN ${whenPair.second.toSQL(queryBuilder)}")
        }

        sb.append(" ELSE ${elseResult.toSQL(queryBuilder)} END")
        return sb.toString()
    }
}

class GroupConcat(val expr: Column<*>, val separator: String?, val distinct: Boolean, vararg val orderBy: Pair<Expression<*>,Boolean>): Function<String?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return buildString {
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
    }

    override val columnType: IColumnType = StringColumnType()
}
