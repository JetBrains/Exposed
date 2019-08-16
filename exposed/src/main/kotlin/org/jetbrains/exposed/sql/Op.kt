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
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            when(currentDialect) {
                is SQLServerDialect, is OracleDialect -> Op.build { booleanLiteral(true) eq booleanLiteral(true) }.toQueryBuilder(this)
                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(true))
            }
        }
    }
    object FALSE : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            when(currentDialect) {
                is SQLServerDialect, is OracleDialect -> Op.build { booleanLiteral(true) eq booleanLiteral(false) }.toQueryBuilder(this)
                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(false))
            }
        }
    }
}

infix fun Op<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
    this is AndOp && op is AndOp -> apply {
        expressions.add(op.expr1)
        expressions.addAll(op.expressions)
    }
    this is AndOp -> apply { expressions.add(op) }
    else -> AndOp(this, op)
}

infix fun Op<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = OrOp(this, op)

fun List<Op<Boolean>>.compoundAnd() = reduce { op, nextOp -> op and nextOp }
fun List<Op<Boolean>>.compoundOr() = reduce { op, nextOp -> op or nextOp }

fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

class IsNullOp(val expr: Expression<*>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr, " IS NULL") }
}

class IsNotNullOp(val expr: Expression<*>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr, " IS NOT NULL") }
}

class LiteralOp<T>(override val columnType: IColumnType, val value: T): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { +columnType.valueToString(value) }
}

class Between(val expr: Expression<*>, val from: LiteralOp<*>, val to: LiteralOp<*>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(expr, " BETWEEN ", from, " AND ", to)
    }
}

class NoOpConversion<T, S>(val expr: Expression<T>, override val columnType: IColumnType): ExpressionWithColumnType<S>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { +expr }
}

class InListOrNotInListOp<T>(val expr: ExpressionWithColumnType<T>, val list: Iterable<T>, val isInList: Boolean = true): Op<Boolean>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        list.iterator().let { i ->
            if (!i.hasNext()) {
                val op = if (isInList) Op.FALSE else Op.TRUE
                +op
            } else {
                val first = i.next()
                if (!i.hasNext()) {
                    append(expr)
                    when {
                        isInList -> append(" = ")
                        else -> append(" != ")
                    }
                    registerArgument(expr.columnType, first)
                } else {
                    append(expr)
                    when {
                        isInList -> append(" IN (")
                        else -> append(" NOT IN (")
                    }

                    registerArguments(expr.columnType, list)

                    append(")")
                }
            }
        }
    }
}

class InSubQueryOp<T>(val expr: Expression<T>, val query: Query): Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(expr, " IN (")
        query.prepareSQL(this)
        +")"
    }
}

class QueryParameter<T>(val value: T, val sqlType: IColumnType) : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { registerArgument(sqlType, value) }
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

private fun QueryBuilder.appendExpression(expr: Expression<*>) {
    if (expr is OrOp) {
        append("(", expr, ")")
    } else {
        append(expr)
    }
}

abstract class ComparisonOp(val expr1: Expression<*>, val expr2: Expression<*>, val opSign: String) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        appendExpression(expr1)
        append(" $opSign ")
        appendExpression(expr2)
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

@Deprecated("Use not(RegexpOp()) instead", level = DeprecationLevel.ERROR)
class NotRegexpOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "NOT REGEXP")

class AndOp(internal val expr1: Expression<*>, vararg expr: Expression<*>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        appendExpression(expr1)
        expressions.forEach {
            append(" AND ")
            appendExpression(it)
        }
    }

    internal val expressions = expr.toMutableList()

}

class RegexpOp<T:String?>(val expr1: Expression<T>, val expr2: Expression<String>, val caseSensitive: Boolean) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.regexp(expr1, expr2, caseSensitive, queryBuilder)
    }
}

class OrOp(val expr1: Expression<Boolean>, val expr2: Expression<Boolean>): Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        appendExpression(expr1)
        append(" OR ")
        appendExpression(expr2)
    }
}

class NotOp<T>(val expr: Expression<T>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("NOT (", expr, ")") }
}

class exists(val query: Query) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("EXISTS (")
        query.prepareSQL(queryBuilder)
        append(")")
    }
}

class notExists(val query: Query) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("NOT EXISTS (")
        query.prepareSQL(queryBuilder)
        append(")")
    }
}

class PlusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr1, '+', expr2) }
}

class MinusOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr1, '-', expr2) }
}

class TimesOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append(expr1, '*', expr2) }
}

class DivideOp<T, S: T>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append('(', expr1, " / ", expr2, ')') }
}

class ModOp<T:Number?, S: Number?>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when(currentDialectIfAvailable) {
            is OracleDialect -> append("MOD(", expr1, ", ", expr2, ")")
            else -> append('(', expr1, " % ", expr2, ')')
        }
    }
}