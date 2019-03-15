package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import org.joda.time.DateTime

abstract class Op<T> : Expression<T>() {
    companion object {
        inline fun <T> build(op: SqlExpressionBuilder.() -> Op<T>): Op<T> = SqlExpressionBuilder.op()
    }

    object TRUE : Op<Boolean>() {
        override fun toSQL(queryBuilder: QueryBuilder) = when(currentDialect) {
            is SQLServerDialect, is OracleDialect -> Op.build { booleanLiteral(true) eq booleanLiteral(true) }.toSQL(queryBuilder)
            else -> currentDialect.dataTypeProvider.booleanToStatementString(true)
        }
    }
    object FALSE : Op<Boolean>() {
        override fun toSQL(queryBuilder: QueryBuilder) = when(currentDialect) {
            is SQLServerDialect, is OracleDialect -> Op.build { booleanLiteral(true) eq booleanLiteral(false) }.toSQL(queryBuilder)
            else -> currentDialect.dataTypeProvider.booleanToStatementString(false)
        }
    }
}

infix fun Op<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = AndOp(this, op)

infix fun Op<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = OrOp(this, op)

fun List<Op<Boolean>>.compoundAnd() = reduce { op, nextOp -> op and nextOp }
fun List<Op<Boolean>>.compoundOr() = reduce { op, nextOp -> op or nextOp }

fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

class IsNullOp(val expr: Expression<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)} IS NULL"
}

class IsNotNullOp(val expr: Expression<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)} IS NOT NULL"
}

class LiteralOp<T>(override val columnType: IColumnType, val value: T): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String = columnType.valueToString(value)
}

class Between(val expr: Expression<*>, val from: LiteralOp<*>, val to: LiteralOp<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String =
        "${expr.toSQL(queryBuilder)} BETWEEN ${from.toSQL(queryBuilder)} AND ${to.toSQL(queryBuilder)}"
}

class NoOpConversion<T, S>(val expr: Expression<T>, override val columnType: IColumnType): ExpressionWithColumnType<S>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = expr.toSQL(queryBuilder)
}

class InListOrNotInListOp<T>(val expr: ExpressionWithColumnType<T>, val list: Iterable<T>, val isInList: Boolean = true): Op<Boolean>() {

    override fun toSQL(queryBuilder: QueryBuilder): String = buildString {
        list.iterator().let { i ->
            if (!i.hasNext()) {
                val op = if (isInList) Op.FALSE else Op.TRUE
                append(op.toSQL(queryBuilder))
            } else {
                val first = i.next()
                if (!i.hasNext()) {
                    append(expr.toSQL(queryBuilder))
                    when {
                        isInList -> append(" = ")
                        else -> append(" != ")
                    }
                    append(queryBuilder.registerArgument(expr.columnType, first))
                } else {
                    append(expr.toSQL(queryBuilder))
                    when {
                        isInList -> append(" IN (")
                        else -> append(" NOT IN (")
                    }

                    queryBuilder.registerArguments(expr.columnType, list).joinTo(this)

                    append(")")
                }
            }
        }
    }
}

class QueryParameter<T>(val value: T, val sqlType: IColumnType) : Expression<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = queryBuilder.registerArgument(sqlType, value)
}

fun <T:Comparable<T>> idParam(value: EntityID<T>, column: Column<EntityID<T>>): Expression<EntityID<T>> = QueryParameter(value, EntityIDColumnType(column))
fun booleanParam(value: Boolean): Expression<Boolean> = QueryParameter(value, BooleanColumnType())
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())
fun stringParam(value: String): Expression<String> = QueryParameter(value, VarCharColumnType())
fun dateParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(false))
fun dateTimeParam(value: DateTime): Expression<DateTime> = QueryParameter(value, DateColumnType(true))

fun booleanLiteral(value: Boolean): LiteralOp<Boolean> = LiteralOp(BooleanColumnType(), value)
fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerColumnType(), value)
fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongColumnType(), value)
fun stringLiteral(value: String): LiteralOp<String> = LiteralOp(VarCharColumnType(), value)
fun dateLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(false), value)
fun dateTimeLiteral(value: DateTime): LiteralOp<DateTime> = LiteralOp(DateColumnType(true), value)

abstract class ComparisonOp(val expr1: Expression<*>, val expr2: Expression<*>, val opSign: String) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder) = buildString {
        if (expr1 is OrOp) {
            append("(").append(expr1.toSQL(queryBuilder)).append(")")
        } else {
            append(expr1.toSQL(queryBuilder))
        }
        append(" $opSign ")
        if (expr2 is OrOp) {
            append("(").append(expr2.toSQL(queryBuilder)).append(")")
        } else {
            append(expr2.toSQL(queryBuilder))
        }
    }
}

class EqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "=")
class NeqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<>")
class LessOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<")
class LessEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<=")
class GreaterOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">")
class GreaterEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">=")
class LikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "LIKE")
class NotLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "NOT LIKE")
class RegexpOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "REGEXP")
class NotRegexpOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "NOT REGEXP")
class AndOp(expr1: Expression<Boolean>, expr2: Expression<Boolean>) : ComparisonOp(expr1, expr2, "AND")

class OrOp(val expr1: Expression<Boolean>, val expr2: Expression<Boolean>): Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder) : String = buildString {
        if (expr1 is OrOp) {
            append(expr1.toSQL(queryBuilder))
        } else {
            append('(').append(expr1.toSQL(queryBuilder)).append(")")
        }
        append(" OR ")

        if (expr2 is OrOp) {
            append(expr2.toSQL(queryBuilder))
        } else {
            append('(').append(expr2.toSQL(queryBuilder)).append(")")
        }
    }
}

class NotOp<T>(val expr: Expression<T>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "NOT (${expr.toSQL(queryBuilder)})"
}

class exists(val query: Query) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "EXISTS (${query.prepareSQL(queryBuilder)})"
}

class notExists(val query: Query) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "NOT EXISTS (${query.prepareSQL(queryBuilder)})"
}

class PlusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "${expr1.toSQL(queryBuilder)}+${expr2.toSQL(queryBuilder)}"
}

class MinusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder) = "${expr1.toSQL(queryBuilder)}-${expr2.toSQL(queryBuilder)}"
}

class TimesOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String = "(${expr1.toSQL(queryBuilder)}) * (${expr2.toSQL(queryBuilder)})"
}

class DivideOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String =
            "(${expr1.toSQL(queryBuilder)}) / (${expr2.toSQL(queryBuilder)})"
}

class ModOp<T:Number?, S: Number?>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder):String = when(currentDialectIfAvailable) {
        is OracleDialect -> "MOD(${expr1.toSQL(queryBuilder)}, ${expr2.toSQL(queryBuilder)})"
        else -> "(${expr1.toSQL(queryBuilder)}) % (${expr2.toSQL(queryBuilder)})"
    }

}