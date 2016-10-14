package org.jetbrains.exposed.sql
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.*

abstract class Function<T>(): ExpressionWithColumnType<T>()

class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COUNT(${if (distinct) "DISTINCT " else ""}${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = IntegerColumnType();
}

class Date(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "DATE(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DateColumnType(false);
}

class CurrentDateTime(): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "CURRENT_TIMESTAMP"
    override val columnType: ColumnType = DateColumnType(false)
}

class Month(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MONTH(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DateColumnType(false);
}

class LowerCase<T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "LOWER(${expr.toSQL(queryBuilder)})"
    }

    override val columnType = StringColumnType()
}

class UpperCase<T: String?>(val expr: Expression<T>) : Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "UPPER(${expr.toSQL(queryBuilder)})"
    }

    override val columnType = StringColumnType()
}

class Min<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MIN(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

class Max<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MAX(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

class Avg<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "AVG(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevPop<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "STDDEV_POP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class StdDevSamp<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "STDDEV_SAMP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarPop<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "VAR_POP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class VarSamp<out T>(val expr: Expression<T>, scale: Int): Function<BigDecimal?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "VAR_SAMP(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DecimalColumnType(Int.MAX_VALUE, scale)
}

class Sum<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T?>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "SUM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

class Coalesce<T:Any>(val expr: ExpressionWithColumnType<out T?>, val alternate: ExpressionWithColumnType<out T>): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COALESCE(${expr.toSQL(queryBuilder)}, ${alternate.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = alternate.columnType
}

class Substring(val expr: Expression<*>, val start: Expression<Int>, val length: Expression<Int>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "${currentDialect.functionProvider.substring}(${expr.toSQL(queryBuilder)}, ${start.toSQL(queryBuilder)}, ${length.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = StringColumnType()
}

class Trim(val expr: Expression<*>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "TRIM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = StringColumnType()
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

    override val columnType: ColumnType = StringColumnType()
}
