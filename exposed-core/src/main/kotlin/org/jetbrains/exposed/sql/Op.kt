package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable

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

infix fun Expression<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
    this is AndOp && op is AndOp -> AndOp(ArrayList(expressions).apply{ addAll(op.expressions) })
    this is AndOp -> AndOp(ArrayList(expressions).apply{ add(op) })
    op is AndOp -> AndOp(ArrayList<Expression<Boolean>>(op.expressions.size + 1).apply {
        add(this@and)
        addAll(op.expressions)
    })
    else -> AndOp(ArrayList<Expression<Boolean>>().apply{
        add(this@and)
        add(op)
    })
}

infix fun Op<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = when {
    this is OrOp && op is OrOp -> OrOp(ArrayList(expressions).apply{ addAll(op.expressions) })
    this is OrOp -> OrOp(ArrayList(expressions).apply{ add(op) })
    op is OrOp -> OrOp(ArrayList<Expression<Boolean>>(op.expressions.size + 1).apply {
        add(this@or)
        addAll(op.expressions)
    })
    else -> OrOp(ArrayList<Expression<Boolean>>().apply{
        add(this@or)
        add(op)
    })
}

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

class NotInSubQueryOp<T>(val expr: Expression<T>, val query: Query) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(expr, " NOT IN (")
        query.prepareSQL(this)
        +")"
    }
}

class QueryParameter<T>(val value: T, val sqlType: IColumnType) : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { registerArgument(sqlType, value) }
}

fun <T:Comparable<T>> idParam(value: EntityID<T>, column: Column<EntityID<T>>): Expression<EntityID<T>> = QueryParameter(value, EntityIDColumnType(column))
fun booleanParam(value: Boolean): Expression<Boolean> = QueryParameter(value, BooleanColumnType())
fun shortParam(value: Short): Expression<Short> = QueryParameter(value, ShortColumnType())
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())
fun floatParam(value: Float): Expression<Float> = QueryParameter(value, FloatColumnType())
fun doubleParam(value: Double): Expression<Double> = QueryParameter(value, DoubleColumnType())
fun stringParam(value: String): Expression<String> = QueryParameter(value, VarCharColumnType())

fun booleanLiteral(value: Boolean): LiteralOp<Boolean> = LiteralOp(BooleanColumnType(), value)
fun shortLiteral(value: Short): LiteralOp<Short> = LiteralOp(ShortColumnType(), value)
fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerColumnType(), value)
fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongColumnType(), value)
fun floatLiteral(value: Float): LiteralOp<Float> = LiteralOp(FloatColumnType(), value)
fun doubleLiteral(value: Double): LiteralOp<Double> = LiteralOp(DoubleColumnType(), value)
fun stringLiteral(value: String): LiteralOp<String> = LiteralOp(VarCharColumnType(), value)

private fun QueryBuilder.appendExpression(expr: Expression<*>) {
    if (expr is CompoundBooleanOp<*>) {
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

abstract class CompoundBooleanOp<T:CompoundBooleanOp<T>>(private val operator: String, internal val expressions: List<Expression<Boolean>>) : Op<Boolean> () {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        expressions.forEachIndexed { indx, el ->
            if (indx > 0) append(operator)
            appendExpression(el)
        }
    }
}

class AndOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" AND ", expressions)

class OrOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" OR ", expressions)

class RegexpOp<T:String?>(val expr1: Expression<T>, val expr2: Expression<String>, val caseSensitive: Boolean) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.regexp(expr1, expr2, caseSensitive, queryBuilder)
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

class PlusOp<T, S: T>(expr1: Expression<T>, expr2: Expression<S>, override val columnType: IColumnType): CustomOperator<T>("+", columnType, expr1, expr2)
class MinusOp<T, S: T>(expr1: Expression<T>, expr2: Expression<S>, override val columnType: IColumnType): CustomOperator<T>("-", columnType, expr1, expr2)
class TimesOp<T, S: T>(expr1: Expression<T>, expr2: Expression<S>, override val columnType: IColumnType): CustomOperator<T>("*", columnType, expr1, expr2)
class DivideOp<T, S: T>(expr1: Expression<T>, expr2: Expression<S>, override val columnType: IColumnType): CustomOperator<T>("/", columnType, expr1, expr2)

class ModOp<T:Number?, S: Number?>(val expr1: Expression<T>, val expr2: Expression<S>, override val columnType: IColumnType): ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when(currentDialectIfAvailable) {
            is OracleDialect -> append("MOD(", expr1, ", ", expr2, ")")
            else -> append('(', expr1, " % ", expr2, ')')
        }
    }
}