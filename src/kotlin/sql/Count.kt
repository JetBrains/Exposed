package kotlin.sql

import java.util.ArrayList

data class Count(val expr: Expression<*>): Function<Int>() {
    override fun toSQL(): String {
        return "COUNT(${expr.toSQL()})"
    }

    override val columnType: ColumnType = IntegerColumnType();
}

data class Min<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "MIN(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}

data class Max<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "MAX(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}

data class Sum<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "SUM(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}

data class Substring(val expr: Expression<String>, val start: Expression<Int>, val length: Expression<Int>): Function<String>() {
    override fun toSQL(): String {
        return "SUBSTRING(${expr.toSQL()}, ${start.toSQL()}, ${length.toSQL()})"
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

    fun Else(e: Expression<T>) : Field<T> {
        return CaseWhenElse<T>(this, e)
    }
}

class CaseWhenElse<T> (val caseWhen: CaseWhen<T>, val elseResult: Expression<T>) : Field<T>() {
    override fun toSQL(): String {
        val sb = StringBuilder()
        sb.append("CASE")
        if (caseWhen.value != null)
            sb.append( " ${caseWhen.value.toSQL()}")

        for (whenPair in caseWhen.cases) {
            sb.append(" WHEN ${whenPair.first.toSQL()} THEN ${whenPair.second.toSQL()}")
        }

        sb.append(" ELSE ${elseResult.toSQL()} END")
        return sb.toString()
    }
}
