package kotlin.sql
import org.joda.time.DateTime
import java.util.ArrayList

abstract class Function<T>(): ExpressionWithColumnType<T>

data class Count(val expr: Expression<*>, val distinct: Boolean = false): Function<Int>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COUNT(${if (distinct) "DISTINCT " else ""}${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = IntegerColumnType();
}

data class Date(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "DATE(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DateColumnType(false);
}

data class Month(val expr: Expression<DateTime?>): Function<DateTime>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MONTH(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = DateColumnType(false);
}

data class Min<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MIN(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

data class Max<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "MAX(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

data class Sum<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "SUM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = _columnType
}

data class Coalesce<T:Any>(val expr: ExpressionWithColumnType<out T?>, val alternate: ExpressionWithColumnType<out T>): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "COALESCE(${expr.toSQL(queryBuilder)}, ${alternate.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = alternate.columnType
}

data class Substring(val expr: Expression<*>, val start: Expression<Int>, val length: Expression<Int>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "SUBSTRING(${expr.toSQL(queryBuilder)}, ${start.toSQL(queryBuilder)}, ${length.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = StringColumnType()
}

data class Trim(val expr: Expression<*>): Function<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "TRIM(${expr.toSQL(queryBuilder)})"
    }

    override val columnType: ColumnType = StringColumnType()
}

data class Distinct<T>(val expr: Expression<T>, override val columnType: ColumnType): Function<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "DISTINCT(${expr.toSQL(queryBuilder)})"
    }
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

class CaseWhenElse<T> (val caseWhen: CaseWhen<T>, val elseResult: Expression<T>) : Expression<T> {
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
