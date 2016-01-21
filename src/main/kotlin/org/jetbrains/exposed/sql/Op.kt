package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.*
import org.joda.time.DateTime

abstract class Op<T>() : Expression<T>() {
    companion object {
        inline fun <T> build(op: SqlExpressionBuilder.()-> Op<T>): Op<T> {
            return SqlExpressionBuilder.op()
        }
    }
}

infix fun Op<Boolean>.and(op: Expression<Boolean>): Op<Boolean> {
    return AndOp(this, op)
}

infix fun Op<Boolean>.or(op: Expression<Boolean>): Op<Boolean> {
    return OrOp(this, op)
}

class IsNullOp(val expr: Expression<*>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "${expr.toSQL(queryBuilder)} IS NULL"
    }
}

class IsNotNullOp(val expr: Expression<*>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "${expr.toSQL(queryBuilder)} IS NOT NULL"
    }
}

class LiteralOp<T>(override val columnType: ColumnType, val value: Any): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return columnType.valueToString(value)
    }
}

class Between(val expr: Expression<*>, val from: LiteralOp<*>, val to: LiteralOp<*> ): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "${expr.toSQL(queryBuilder)} BETWEEN ${from.toSQL(queryBuilder)} AND ${to.toSQL(queryBuilder)}"
    }
}

class NoOpConversion<T, S>(val expr: Expression<T>, override val columnType: ColumnType): ExpressionWithColumnType<S>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return expr.toSQL(queryBuilder)
    }
}

class InListOrNotInListOp<T>(val expr: ExpressionWithColumnType<T>, val list: List<T>, val isInList: Boolean = true): Op<Boolean>() {

    override fun toSQL(queryBuilder: QueryBuilder): String {
        val sb = StringBuilder()

        when (list.size) {
            0 -> when {
                isInList ->  sb.append(" FALSE")
                else -> sb.append(" TRUE")
            }

            1 -> {
                sb.append(expr.toSQL(queryBuilder))
                when {
                    isInList ->  sb.append(" = ")
                    else -> sb.append(" != ")
                }
                sb.append(queryBuilder.registerArgument(list.first(), expr.columnType))
            }

            else -> {
                sb.append(expr.toSQL(queryBuilder))
                when {
                    isInList -> sb.append(" IN (")
                    else -> sb.append(" NOT IN (")
                }

                list.map {
                    queryBuilder.registerArgument(it, expr.columnType)
                }.joinTo(sb)

                sb.append(")")
            }
        }

        return sb.toString()
    }
}

class QueryParameter<T>(val value: T, val sqlType: ColumnType) : Expression<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return queryBuilder.registerArgument(value, sqlType)
    }
}

fun idParam(value: EntityID): Expression<EntityID> = QueryParameter(value, EntityIDColumnType(value.table))
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())
fun stringParam(value: String): Expression<String> = QueryParameter(value, StringColumnType())
fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))

fun intLiteral(value: Int) : LiteralOp<Int> = LiteralOp (IntegerColumnType(), value)
fun longLiteral(value: Long) : LiteralOp<Long> = LiteralOp(LongColumnType(), value)
fun stringLiteral(value: String) : LiteralOp<String> = LiteralOp(StringColumnType(), value)
fun dateTimeLiteral(value: DateTime) : LiteralOp<DateTime> = LiteralOp(DateColumnType(true), value)

abstract class ComparisonOp(val expr1: Expression<*>, val expr2: Expression<*>, val opSign: String): Op<Boolean>() {
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

class EqOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "=")
class NeqOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "<>")
class LessOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "<")
class LessEqOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "<=")
class GreaterOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, ">")
class GreaterEqOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, ">=")
class LikeOp (expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "LIKE")
class NotLikeOp (expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "NOT LIKE")
class RegexpOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "REGEXP")
class NotRegexpOp(expr1: Expression<*>, expr2: Expression<*>): ComparisonOp(expr1, expr2, "NOT REGEXP")

class AndOp(val expr1: Expression<Boolean>, val expr2: Expression<Boolean>): Op<Boolean>() {
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
        return "(" + expr1.toSQL(queryBuilder) + ") or (" + expr2.toSQL(queryBuilder) + ")"
    }
}

class exists(val query: Query) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "EXISTS (${query.prepareSQL(QueryBuilder(false))})"
    }
}

class notExists(val query: Query) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        return "NOT EXISTS (${query.prepareSQL(QueryBuilder(false))})"
    }
}

class PlusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: ColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return expr1.toSQL(queryBuilder) + "+" + expr2.toSQL(queryBuilder)
    }
}

class MinusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: ColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return expr1.toSQL(queryBuilder) + "-" + expr2.toSQL(queryBuilder)
    }
}

class TimesOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: ColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "(${expr1.toSQL(queryBuilder)}) * (${expr2.toSQL(queryBuilder)})"
    }
}

class DivideOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: ColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String {
        return "(${expr1.toSQL(queryBuilder)}) / (${expr2.toSQL(queryBuilder)})"
    }

    override fun toString(): String {
        error("!")
    }
}
