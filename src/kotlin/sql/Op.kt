package kotlin.sql

import org.joda.time.DateTime

abstract class Op<T>() : Expression<T> {
    fun and(op: Expression<T>): Op<Boolean> {
        return AndOp(this, op)
    }

    fun or(op: Expression<T>): Op<Boolean> {
        return OrOp(this, op)
    }
}

class IsNullOp(val column: Column<*>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "${Session.get().fullIdentity(column)} IS NULL"
    }
}

class IsNotNullOp(val column: Column<*>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "${Session.get().fullIdentity(column)} IS NOT NULL"
    }
}

class LiteralOp<T>(val columnType: ColumnType, val value: Any): Expression<T> {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return columnType.valueToString(value)
    }
}

class QueryParameter<T>(val value: T, val sqlType: ColumnType) : Field<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return queryBuilder.registerArgument(value, sqlType)
    }
}

fun intParam(value: Int): Field<Int> = QueryParameter(value, IntegerColumnType())
fun longParam(value: Long): Field<Long> = QueryParameter(value, LongColumnType())
fun stringParam(value: String): Field<String> = QueryParameter(value, StringColumnType())
fun dateParam(value: DateTime): Field<DateTime> = QueryParameter(value, DateColumnType(false))

fun intLiteral(value: Int) : LiteralOp<Int> = LiteralOp<Int> (IntegerColumnType(), value)
fun longLiteral(value: Long) : LiteralOp<Long> = LiteralOp<Long>(LongColumnType(), value)
fun stringLiteral(value: String) : LiteralOp<String> = LiteralOp<String>(StringColumnType(), value)

abstract class ComparisonOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, val opSign: String): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        val sb = StringBuilder()
        if (expr1 is OrOp<*>) {
            sb.append("(").append(expr1.toSQL(queryBuilder)).append(")")
        } else {
            sb.append(expr1.toSQL(queryBuilder))
        }
        sb.append(" $opSign ")
        if (expr2 is OrOp<*>) {
            sb.append("(").append(expr2.toSQL(queryBuilder)).append(")")
        } else {
            sb.append(expr2.toSQL(queryBuilder))
        }
        return sb.toString()
    }
}

class EqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "=") {
}

class NeqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<>") {
}

class LessOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<") {
}

class LessEqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "<=") {
}

class GreaterOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, ">") {
}

class GreaterEqOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, ">=") {
}

class LikeOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "LIKE") {
}

class RegexpOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "REGEXP") {
}

class NotRegexpOp<out T>(expr1: Expression<T>, expr2: Expression<T>): ComparisonOp<T>(expr1, expr2, "NOT REGEXP") {
}

class AndOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        val sb = StringBuilder()
        if (expr1 is OrOp<*>) {
            sb.append("(").append(expr1.toSQL(queryBuilder)).append(")")
        } else {
            sb.append(expr1.toSQL(queryBuilder))
        }
        sb.append(" and ")
        if (expr2 is OrOp<*>) {
            sb.append("(").append(expr2.toSQL(queryBuilder)).append(")")
        } else {
            sb.append(expr2.toSQL(queryBuilder))
        }
        return sb.toString()
    }
}

class OrOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return expr1.toSQL(queryBuilder) + " or " + expr2.toSQL(queryBuilder)
    }
}

class exists(val query: Query) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "EXISTS (${query.toSQL(QueryBuilder(false))})"
    }
}
