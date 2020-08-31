package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import java.math.BigDecimal

/**
 * Represents an SQL operator.
 */
abstract class Op<T> : Expression<T>() {
    companion object {
        /** Builds a new operator using provided [op]. */
        inline fun <T> build(op: SqlExpressionBuilder.() -> Op<T>): Op<T> = SqlExpressionBuilder.op()
    }

    /** Boolean operator corresponding to the SQL value `TRUE` */
    object TRUE : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
            when (currentDialect) {
                is SQLServerDialect, is OracleDialect -> build { booleanLiteral(true) eq booleanLiteral(true) }.toQueryBuilder(this)
                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(true))
            }
        }
    }

    /** Boolean operator corresponding to the SQL value `FALSE` */
    object FALSE : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
            when (currentDialect) {
                is SQLServerDialect, is OracleDialect -> build { booleanLiteral(true) eq booleanLiteral(false) }.toQueryBuilder(this)
                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(false))
            }
        }
    }
}


// Logical Operators

/**
 * Represents a logical operator that inverts the specified boolean [expr].
 */
class NotOp<T>(
    /** Returns the expression being inverted. */
    val expr: Expression<T>
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("NOT (", expr, ")") }
}


/**
 * Marker interface which indicates that expression should be wrapped with braces when used in compound operators
 */
interface ComplexExpression

/**
 * Represent a logical operator that performs an operation between all the specified [expressions].
 * This is the base class for the `and` and `or` operators:
 *
 * @see AndOp
 * @see OrOp
 */
abstract class CompoundBooleanOp<T : CompoundBooleanOp<T>>(
    private val operator: String,
    internal val expressions: List<Expression<Boolean>>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        expressions.appendTo(this, separator = operator) { appendExpression(it) }
    }
}

/**
 * Represents a logical operator that performs an `and` operation between all the specified [expressions].
 */
class AndOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" AND ", expressions)

/**
 * Represents a logical operator that performs an `or` operation between all the specified [expressions].
 */
class OrOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" OR ", expressions)

/** Returns the inverse of this boolean expression. */
fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

/** Returns the result of performing a logical `and` operation between this expression and the [op]. */
infix fun Expression<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
    this is AndOp && op is AndOp -> AndOp(expressions + op.expressions)
    this is AndOp -> AndOp(expressions + op)
    op is AndOp -> AndOp(ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
        it.add(this)
        it.addAll(op.expressions)
    })
    else -> AndOp(listOf(this, op))
}

/** Returns the result of performing a logical `or` operation between this expression and the [op]. */
infix fun Expression<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = when {
    this is OrOp && op is OrOp -> OrOp(expressions + op.expressions)
    this is OrOp -> OrOp(expressions + op)
    op is OrOp -> OrOp(ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
        it.add(this)
        it.addAll(op.expressions)
    })
    else -> OrOp(listOf(this, op))
}

/** Reduces this list to a single expression by performing an `and` operation between all the expressions in the list. */
fun List<Op<Boolean>>.compoundAnd(): Op<Boolean> = reduce(Op<Boolean>::and)

/** Reduces this list to a single expression by performing an `or` operation between all the expressions in the list. */
fun List<Op<Boolean>>.compoundOr(): Op<Boolean> = reduce(Op<Boolean>::or)


// Comparison Operators

/**
 * Represents a comparison between [expr1] and [expr2] using the given SQL [opSign].
 */
abstract class ComparisonOp(
    /** Returns the left-hand side operand. */
    val expr1: Expression<*>,
    /** Returns the right-hand side operand. */
    val expr2: Expression<*>,
    /** Returns the symbol of the comparison operation. */
    val opSign: String
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        appendExpression(expr1)
        append(" $opSign ")
        appendExpression(expr2)
    }
}

/**
 * Represents an SQL operator that checks if [expr1] is equals to [expr2].
 */
class EqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "=")

/**
 * Represents an SQL operator that checks if [expr1] is not equals to [expr2].
 */
class NeqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<>")

/**
 * Represents an SQL operator that checks if [expr1] is less than [expr2].
 */
class LessOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<")

/**
 * Represents an SQL operator that checks if [expr1] is less than or equal to [expr2].
 */
class LessEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<=")

/**
 * Represents an SQL operator that checks if [expr1] is greater than [expr2].
 */
class GreaterOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">")

/**
 * Represents an SQL operator that checks if [expr1] is greater than or equal to [expr2].
 */
class GreaterEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">=")

/**
 * Represents an SQL operator that checks if the specified [expr] is between the values [from] and [to].
 */
class Between(
    /** The expression being checked. */
    val expr: Expression<*>,
    /** Returns the lower limit of the range to check against. */
    val from: Expression<*>,
    /** Returns the upper limit of the range to check against. */
    val to: Expression<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " BETWEEN ", from, " AND ", to) }
}

/**
 * Represents an SQL operator that checks if the specified [expr] is null.
 */
class IsNullOp(
    /** The expression being checked. */
    val expr: Expression<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " IS NULL") }
}

/**
 * Represents an SQL operator that checks if the specified [expr] is not null.
 */
class IsNotNullOp(
    /** The expression being checked. */
    val expr: Expression<*>
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " IS NOT NULL") }
}


// Mathematical Operators

/**
 * Represents an SQL operator that adds [expr2] to [expr1].
 */
class PlusOp<T, S : T>(
    /** The left-hand side operand. */
    expr1: Expression<T>,
    /** The right-hand side operand. */
    expr2: Expression<S>,
    /** The column type of this expression. */
    columnType: IColumnType
) : CustomOperator<T>("+", columnType, expr1, expr2)

/**
 * Represents an SQL operator that subtracts [expr2] from [expr1].
 */
class MinusOp<T, S : T>(
    /** The left-hand side operand. */
    expr1: Expression<T>,
    /** The right-hand side operand. */
    expr2: Expression<S>,
    /** The column type of this expression. */
    columnType: IColumnType
) : CustomOperator<T>("-", columnType, expr1, expr2)

/**
 * Represents an SQL operator that multiplies [expr1] by [expr2].
 */
class TimesOp<T, S : T>(
    /** The left-hand side operand. */
    expr1: Expression<T>,
    /** The right-hand side operand. */
    expr2: Expression<S>,
    /** The column type of this expression. */
    columnType: IColumnType
) : CustomOperator<T>("*", columnType, expr1, expr2)

/**
 * Represents an SQL operator that divides [expr1] by [expr2].
 */
class DivideOp<T, S : T>(
    /** The left-hand side operand. */
    private val dividend: Expression<T>,
    /** The right-hand side operand. */
    private val divisor: Expression<S>,
    /** The column type of this expression. */
    columnType: IColumnType
) : CustomOperator<T>("/", columnType, dividend, divisor) {
    companion object {
        fun <T:BigDecimal?, S : T>  DivideOp<T, S>.withScale(scale: Int) : DivideOp<T, S> {
            val precision = (columnType as DecimalColumnType).precision + scale
            val decimalColumnType = DecimalColumnType(precision, scale)

            val newExpression = (dividend as? LiteralOp<BigDecimal>)?.value?.takeIf { it.scale() == 0 }?.let {
                decimalLiteral(it.setScale(1)) // it is needed to treat dividend as decimal instead of integer in SQL
            } ?: dividend

            return DivideOp(newExpression as Expression<T>, divisor, decimalColumnType)
        }
    }
}


/**
 * Represents an SQL operator that calculates the remainder of dividing [expr1] by [expr2].
 */
class ModOp<T : Number?, S : Number?>(
    /** Returns the left-hand side operand. */
    val expr1: Expression<T>,
    /** Returns the right-hand side operand. */
    val expr2: Expression<S>,
    override val columnType: IColumnType
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        when (currentDialectIfAvailable) {
            is OracleDialect -> append("MOD(", expr1, ", ", expr2, ")")
            else -> append('(', expr1, " % ", expr2, ')')
        }
    }
}


// Pattern Matching

/**
 * Represents an SQL operator that checks if [expr1] matches [expr2].
 */
class LikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "LIKE")

/**
 * Represents an SQL operator that checks if [expr1] doesn't match [expr2].
 */
class NotLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "NOT LIKE")

/**
 * Represents an SQL operator that checks if [expr1] matches the regular expression [expr2].
 */
class RegexpOp<T : String?>(
    /** Returns the expression being checked. */
    val expr1: Expression<T>,
    /** Returns the regular expression [expr1] is checked against. */
    val expr2: Expression<String>,
    /** Returns `true` if the regular expression is case sensitive, `false` otherwise. */
    val caseSensitive: Boolean
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.regexp(expr1, expr2, caseSensitive, queryBuilder)
    }
}

/**
 * Represents an SQL operator that checks if [expr1] doesn't match the regular expression [expr2].
 */
@Deprecated("Use NotOp(RegexpOp()) instead", ReplaceWith("NotOp(RegexpOp(expr1, expr2, true))"), DeprecationLevel.ERROR)
class NotRegexpOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "NOT REGEXP")


// Subquery Expressions

/**
 * Represents an SQL operator that checks if [query] returns at least one row.
 */
class exists(
    /** Returns the query being checked. */
    val query: Query
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("EXISTS (")
        query.prepareSQL(this)
        append(")")
    }
}

/**
 * Represents an SQL operator that checks if [query] doesn't returns any row.
 */
class notExists(
    /** Returns the query being checked. */
    val query: Query
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("NOT EXISTS (")
        query.prepareSQL(this)
        append(")")
    }
}

/**
 * Represents an SQL operator that checks if [expr] is equals to any row returned from [query].
 */
class InSubQueryOp<T>(
    /** Returns the expression compared to each row of the query result. */
    val expr: Expression<T>,
    /** Returns the query to check against. */
    val query: Query
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(expr, " IN (")
        query.prepareSQL(this)
        +")"
    }
}

/**
 * Represents an SQL operator that checks if [expr] is not equals to any row returned from [query].
 */
class NotInSubQueryOp<T>(
    /** Returns the expression compared to each row of the query result. */
    val expr: Expression<T>,
    /** Returns the query to check against. */
    val query: Query
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(expr, " NOT IN (")
        query.prepareSQL(this)
        +")"
    }
}


// Array Comparisons

/**
 * Represents an SQL operator that checks if [expr] is equals to any element from [list].
 */
class InListOrNotInListOp<T>(
    /** Returns the expression compared to each element of the list. */
    val expr: ExpressionWithColumnType<T>,
    /** Returns the query to check against. */
    val list: Iterable<T>,
    /** Returns `true` if the check is inverted, `false` otherwise. */
    val isInList: Boolean = true
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        list.iterator().let { i ->
            if (!i.hasNext()) {
                if (isInList) {
                    +FALSE
                } else {
                    +TRUE
                }
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


// Literals

/**
 * Represents the specified [value] as an SQL literal, using the specified [columnType] to convert the value.
 */
class LiteralOp<T>(
    override val columnType: IColumnType,
    /** Returns the value being used as a literal. */
    val value: T
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +columnType.valueToString(value) }

}

/** Returns the specified [value] as a boolean literal. */
fun booleanLiteral(value: Boolean): LiteralOp<Boolean> = LiteralOp(BooleanColumnType(), value)

/** Returns the specified [value] as a byte literal. */
fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(ByteColumnType(), value)

/** Returns the specified [value] as a unsigned byte literal. */
@ExperimentalUnsignedTypes
fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(UByteColumnType(), value)

/** Returns the specified [value] as a short literal. */
fun shortLiteral(value: Short): LiteralOp<Short> = LiteralOp(ShortColumnType(), value)

/** Returns the specified [value] as a unsigned short literal. */
@ExperimentalUnsignedTypes
fun ushortLiteral(value: UShort): LiteralOp<UShort> = LiteralOp(UShortColumnType(), value)

/** Returns the specified [value] as an int literal. */
fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerColumnType(), value)

/** Returns the specified [value] as a unsigned int literal. */
@ExperimentalUnsignedTypes
fun uintLiteral(value: UInt): LiteralOp<UInt> = LiteralOp(UIntegerColumnType(), value)

/** Returns the specified [value] as a long literal. */
fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongColumnType(), value)

/** Returns the specified [value] as a unsigned long literal. */
@ExperimentalUnsignedTypes
fun ulongLiteral(value: ULong): LiteralOp<ULong> = LiteralOp(ULongColumnType(), value)

/** Returns the specified [value] as a float literal. */
fun floatLiteral(value: Float): LiteralOp<Float> = LiteralOp(FloatColumnType(), value)

/** Returns the specified [value] as a double literal. */
fun doubleLiteral(value: Double): LiteralOp<Double> = LiteralOp(DoubleColumnType(), value)

/** Returns the specified [value] as a string literal. */
fun stringLiteral(value: String): LiteralOp<String> = LiteralOp(TextColumnType(), value)

/** Returns the specified [value] as a decimal literal. */
fun decimalLiteral(value: BigDecimal) : LiteralOp<BigDecimal> = LiteralOp(DecimalColumnType(value.precision(), value.scale()), value.setScale(1))

// Query Parameters

/**
 * Represents the specified [value] as a query parameter, using the specified [sqlType] to convert the value.
 */
class QueryParameter<T>(
    /** Returns the value being used as a query parameter. */
    val value: T,
    /** Returns the column type of this expression. */
    val sqlType: IColumnType
) : Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { registerArgument(sqlType, value) }
}

/** Returns the specified [value] as a query parameter with the same type as [column]. */
fun <T : Comparable<T>> idParam(value: EntityID<T>, column: Column<EntityID<T>>): Expression<EntityID<T>> = QueryParameter(value, EntityIDColumnType(column))

/** Returns the specified [value] as a boolean query parameter. */
fun booleanParam(value: Boolean): Expression<Boolean> = QueryParameter(value, BooleanColumnType())

/** Returns the specified [value] as a byte query parameter. */
fun byteParam(value: Byte): Expression<Byte> = QueryParameter(value, ByteColumnType())

/** Returns the specified [value] as a unsigned byte query parameter. */
@ExperimentalUnsignedTypes
fun ubyteParam(value: UByte): Expression<UByte> = QueryParameter(value, UByteColumnType())

/** Returns the specified [value] as a short query parameter. */
fun shortParam(value: Short): Expression<Short> = QueryParameter(value, ShortColumnType())

/** Returns the specified [value] as a unsigned short query parameter. */
@ExperimentalUnsignedTypes
fun ushortParam(value: UShort): Expression<UShort> = QueryParameter(value, UShortColumnType())

/** Returns the specified [value] as an int query parameter. */
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())

/** Returns the specified [value] as a unsigned int query parameter. */
@ExperimentalUnsignedTypes
fun uintParam(value: UInt): Expression<UInt> = QueryParameter(value, UIntegerColumnType())

/** Returns the specified [value] as a long query parameter. */
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())

/** Returns the specified [value] as a unsigned long query parameter. */
@ExperimentalUnsignedTypes
fun ulongParam(value: ULong): Expression<ULong> = QueryParameter(value, ULongColumnType())

/** Returns the specified [value] as a float query parameter. */
fun floatParam(value: Float): Expression<Float> = QueryParameter(value, FloatColumnType())

/** Returns the specified [value] as a double query parameter. */
fun doubleParam(value: Double): Expression<Double> = QueryParameter(value, DoubleColumnType())

/** Returns the specified [value] as a string query parameter. */
fun stringParam(value: String): Expression<String> = QueryParameter(value, TextColumnType())

/** Returns the specified [value] as a decimal query parameter. */
fun decimalParam(value: BigDecimal) : Expression<BigDecimal> = QueryParameter(value, DecimalColumnType(value.precision(), value.scale()))


// Misc.

/**
 * Represents an SQL operator that doesn't perform any operation.
 * This is mainly used to change between column types.
 */
class NoOpConversion<T, S>(
    /** Returns the expression whose type is being changed. */
    val expr: Expression<T>,
    override val columnType: IColumnType
) : ExpressionWithColumnType<S>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +expr }
}

private fun QueryBuilder.appendExpression(expr: Expression<*>) {
    if (expr is ComplexExpression) {
        append("(", expr, ")")
    } else {
        append(expr)
    }
}
