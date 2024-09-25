package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.wrap
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal

/**
 * Represents an SQL operator.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Op<T> : Expression<T>() {
    companion object {
        /** Builds a new operator using provided [op]. */
        inline fun <T> build(op: SqlExpressionBuilder.() -> Op<T>): Op<T> = SqlExpressionBuilder.op()

        fun <T> nullOp(): Op<T> = NULL as Op<T>
    }

    internal interface OpBoolean

    /**
     * Boolean operator that always evaluates to the SQL value `TRUE`.
     *
     * **Note** Some databases, like SQL Server and Oracle, do not support conditions like `WHERE 1` or `WHERE TRUE`.
     * When using these databases, this operator will instead produce the condition `1 = 1`.
     */
    object TRUE : Op<Boolean>(), OpBoolean {
        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
            when {
                currentDialect is SQLServerDialect || currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                    build { booleanLiteral(true) eq booleanLiteral(true) }.toQueryBuilder(this)

                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(true))
            }
        }
    }

    /**
     * Boolean operator that always evaluates to the SQL value `FALSE`.
     *
     * **Note** Some databases, like SQL Server and Oracle, do not support conditions like `WHERE 0` or `WHERE FALSE`.
     * When using these databases, this operator will instead produce the condition `1 = 0`.
     */
    object FALSE : Op<Boolean>(), OpBoolean {
        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
            when {
                currentDialect is SQLServerDialect || currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                    build { booleanLiteral(true) eq booleanLiteral(false) }.toQueryBuilder(this)
                else -> append(currentDialect.dataTypeProvider.booleanToStatementString(false))
            }
        }
    }

    internal object NULL : Op<Any>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
            append("NULL")
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
) : Op<Boolean>(), Op.OpBoolean {
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
sealed class CompoundBooleanOp(
    private val operator: String,
    internal val expressions: List<Expression<Boolean>>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        expressions.appendTo(this, separator = operator) { appendExpression(it) }
    }
}

/**
 * Represents a logical operator that performs an `and` operation between all the specified [expressions].
 */
class AndOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp(" AND ", expressions)

/**
 * Represents a logical operator that performs an `or` operation between all the specified [expressions].
 */
class OrOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp(" OR ", expressions)

/** Returns the inverse of this boolean expression. */
fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

/** Returns the result of performing a logical `and` operation between this expression and the [op]. */
infix fun Expression<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
    this is AndOp && op is AndOp -> AndOp(expressions + op.expressions)
    this is AndOp -> AndOp(expressions + op)
    op is AndOp -> AndOp(
        ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
            it.add(this)
            it.addAll(op.expressions)
        }
    )
    else -> AndOp(listOf(this, op))
}

/** Returns the result of performing a logical `or` operation between this expression and the [op]. */
infix fun Expression<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = when {
    this is OrOp && op is OrOp -> OrOp(expressions + op.expressions)
    this is OrOp -> OrOp(expressions + op)
    op is OrOp -> OrOp(
        ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
            it.add(this)
            it.addAll(op.expressions)
        }
    )
    else -> OrOp(listOf(this, op))
}

/**
 * Returns the result of performing a logical `and` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
infix fun Op<Boolean>.andIfNotNull(op: Expression<Boolean>?): Op<Boolean> =
    op?.let { this and it } ?: this

/**
 * Returns the result of performing a logical `or` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
infix fun Op<Boolean>.orIfNotNull(op: Expression<Boolean>?): Op<Boolean> =
    op?.let { this or it } ?: this

/** Reduces this list to a single expression by performing an `and` operation between all the expressions in the list. */
fun List<Op<Boolean>>.compoundAnd(): Op<Boolean> = reduce(Op<Boolean>::and)

/** Reduces this list to a single expression by performing an `or` operation between all the expressions in the list. */
fun List<Op<Boolean>>.compoundOr(): Op<Boolean> = reduce(Op<Boolean>::or)

/** Returns the result of performing a logical `and` operation between this expression and the [op]. */
inline fun Expression<Boolean>.and(op: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> = and(Op.build(op))

/**  Returns the result of performing a logical `or` operation between this expression and the [op].*/
inline fun Expression<Boolean>.or(op: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> = or(Op.build(op))

/** Returns the result of performing a logical `and` operation between this expression and the negate [op]. */
inline fun Expression<Boolean>.andNot(op: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> = and(not(Op.build(op)))

/** Returns the result of performing a logical `or` operation between this expression and the negate [op]. */
inline fun Expression<Boolean>.orNot(op: SqlExpressionBuilder.() -> Op<Boolean>): Op<Boolean> = or(not(Op.build(op)))

/**
 * Returns the result of performing a logical `and` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
inline fun Op<Boolean>.andIfNotNull(op: SqlExpressionBuilder.() -> Op<Boolean>?): Op<Boolean> = andIfNotNull(SqlExpressionBuilder.op())

/**
 * Returns the result of performing a logical `or` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
inline fun Op<Boolean>.orIfNotNull(op: SqlExpressionBuilder.() -> Op<Boolean>?): Op<Boolean> = orIfNotNull(SqlExpressionBuilder.op())

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
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        appendComparison(expr1, expr2, opSign)
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
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " BETWEEN ", from, " AND ", to) }
}

/**
 * Represents an SQL operator that checks if the specified [expr] is null.
 */
class IsNullOp(
    /** The expression being checked. */
    val expr: Expression<*>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " IS NULL") }
}

/**
 * Represents an SQL operator that checks if the specified [expr] is not null.
 */
class IsNotNullOp(
    /** The expression being checked. */
    val expr: Expression<*>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append(expr, " IS NOT NULL") }
}

/**
 * Represents an SQL operator that checks if [expression1] is equal to [expression2], with `null` treated as a comparable value.
 * This comparison never returns null.
 */
class IsNotDistinctFromOp(
    val expression1: Expression<*>,
    val expression2: Expression<*>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialectIfAvailable) {
            is MariaDBDialect, is MysqlDialect -> appendComparison(expression1, expression2, "<=>")
            is OracleDialect -> append("DECODE(", expression1, ", ", expression2, ", 1, 0) = 1")
            is SQLiteDialect -> appendComparison(expression1, expression2, "IS")
            else -> appendComparison(expression1, expression2, "IS NOT DISTINCT FROM")
        }
    }
}

/**
 * Represents an SQL operator that checks if [expression1] is not equal to [expression2], with `null` treated as a comparable value.
 * This comparison never returns null.
 */
class IsDistinctFromOp(
    val expression1: Expression<*>,
    val expression2: Expression<*>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        when (currentDialectIfAvailable) {
            is MariaDBDialect, is MysqlDialect -> {
                +"NOT("
                appendComparison(expression1, expression2, "<=>")
                +")"
            }
            is OracleDialect -> append("DECODE(", expression1, ", ", expression2, ", 1, 0) = 0")
            is SQLiteDialect -> appendComparison(expression1, expression2, "IS NOT")
            else -> appendComparison(expression1, expression2, "IS DISTINCT FROM")
        }
    }
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
    columnType: IColumnType<T & Any>
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
    columnType: IColumnType<T & Any>
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
    columnType: IColumnType<T & Any>
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
    columnType: IColumnType<T & Any>
) : CustomOperator<T>("/", columnType, dividend, divisor) {
    companion object {
        fun <T : BigDecimal?, S : T> DivideOp<T, S>.withScale(scale: Int): DivideOp<T, S> {
            val precision = (columnType as DecimalColumnType).precision + scale
            val decimalColumnType = DecimalColumnType(precision, scale)

            val newExpression = (dividend as? LiteralOp<BigDecimal>)?.value?.takeIf { it.scale() == 0 }?.let {
                decimalLiteral(it.setScale(1)) // it is needed to treat dividend as decimal instead of integer in SQL
            } ?: dividend

            return DivideOp(newExpression as Expression<T>, divisor, decimalColumnType as IColumnType<T & Any>)
        }
    }
}

/**
 * Represents an SQL operator that calculates the remainder of dividing [expr1] by [expr2].
 */
class ModOp<T : Number?, S : Number?, R : Number?>(
    /** Returns the left-hand side operand. */
    val expr1: Expression<T>,
    /** Returns the right-hand side operand. */
    val expr2: Expression<S>,
    override val columnType: IColumnType<R & Any>
) : ExpressionWithColumnType<R>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            when (currentDialectIfAvailable) {
                is OracleDialect -> append("MOD(", expr1, ", ", expr2, ")")
                else -> append('(', expr1, " % ", expr2, ')')
            }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T : Number?, K : EntityID<T>?> originalColumn(expr1: ExpressionWithColumnType<K>): Column<T> {
            return (expr1.columnType as EntityIDColumnType<*>).idColumn as Column<T>
        }

        internal operator fun <T, S : Number, K : EntityID<T>?> invoke(
            expr1: ExpressionWithColumnType<K>,
            expr2: Expression<S>
        ): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> {
            val column = originalColumn(expr1)
            return ModOp(column, expr2, column.columnType)
        }

        internal operator fun <T, S : Number, K : EntityID<T>?> invoke(
            expr1: Expression<S>,
            expr2: ExpressionWithColumnType<K>
        ): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> {
            val column = originalColumn(expr2)
            return ModOp(expr1, column, column.columnType)
        }

        internal operator fun <T, S : Number, K : EntityID<T>?> invoke(
            expr1: ExpressionWithColumnType<K>,
            expr2: S
        ): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> {
            val column = originalColumn(expr1)
            return ModOp(column, column.wrap(expr2), column.columnType)
        }
    }
}

// https://github.com/h2database/h2database/issues/3253
private fun <T> ExpressionWithColumnType<T>.castToExpressionTypeForH2BitWiseIps(e: Expression<out T>, queryBuilder: QueryBuilder) {
    when {
        currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> H2FunctionProvider.cast(e, ByteColumnType(), queryBuilder)
        e is Column<*> || e is LiteralOp<*> -> queryBuilder.append(e)
        else -> currentDialect.functionProvider.cast(e, columnType, queryBuilder)
    }
}

/**
 * Represents an SQL operator that performs a bitwise `and` on [expr1] and [expr2].
 */
class AndBitOp<T, S : T>(
    /** The left-hand side operand. */
    val expr1: Expression<T>,
    /** The right-hand side operand. */
    val expr2: Expression<S>,
    /** The column type of this expression. */
    override val columnType: IColumnType<T & Any>
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        when (val dialect = currentDialectIfAvailable) {
            is OracleDialect -> append("BITAND(", expr1, ", ", expr2, ")")
            is H2Dialect -> {
                when (dialect.isSecondVersion) {
                    false -> append("BITAND(", expr1, ", ", expr2, ")")
                    true -> {
                        +"BITAND("
                        castToExpressionTypeForH2BitWiseIps(expr1, this)
                        +", "
                        castToExpressionTypeForH2BitWiseIps(expr2, this)
                        +")"
                    }
                }
            }
            else -> append('(', expr1, " & ", expr2, ')')
        }
    }
}

/**
 * Represents an SQL operator that performs a bitwise `or` on [expr1] and [expr2].
 */
class OrBitOp<T, S : T>(
    /** The left-hand side operand. */
    val expr1: Expression<T>,
    /** The right-hand side operand. */
    val expr2: Expression<S>,
    /** The column type of this expression. */
    override val columnType: IColumnType<T & Any>
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        when (val dialect = currentDialectIfAvailable) {
            // Oracle doesn't natively support bitwise OR, thus emulate it with 'and'
            is OracleDialect -> append("(", expr1, "+", expr2, "-", AndBitOp(expr1, expr2, columnType), ")")
            is H2Dialect -> {
                when (dialect.isSecondVersion) {
                    false -> append("BITOR(", expr1, ", ", expr2, ")")
                    true -> {
                        +"BITOR("
                        castToExpressionTypeForH2BitWiseIps(expr1, this)
                        +", "
                        castToExpressionTypeForH2BitWiseIps(expr2, this)
                        +")"
                    }
                }
            }
            else -> append('(', expr1, " | ", expr2, ')')
        }
    }
}

/**
 * Represents an SQL operator that performs a bitwise `or` on [expr1] and [expr2].
 */
class XorBitOp<T, S : T>(
    /** The left-hand side operand. */
    val expr1: Expression<T>,
    /** The right-hand side operand. */
    val expr2: Expression<S>,
    /** The column type of this expression. */
    override val columnType: IColumnType<T & Any>
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        when (val dialect = currentDialectIfAvailable) {
            // Oracle and SQLite don't natively support bitwise XOR, thus emulate it with 'or' and 'and'
            is OracleDialect, is SQLiteDialect -> append(
                "(", OrBitOp(expr1, expr2, columnType), "-", AndBitOp(expr1, expr2, columnType), ")"
            )
            is PostgreSQLDialect -> append('(', expr1, " # ", expr2, ')')
            is H2Dialect -> {
                when (dialect.isSecondVersion) {
                    false -> append("BITXOR(", expr1, ", ", expr2, ")")
                    true -> {
                        +"BITXOR("
                        castToExpressionTypeForH2BitWiseIps(expr1, this)
                        +", "
                        castToExpressionTypeForH2BitWiseIps(expr2, this)
                        +")"
                    }
                }
            }
            else -> append('(', expr1, " ^ ", expr2, ')')
        }
    }
}

// Pattern Matching

/**
 * Represents an SQL operator that checks if [expr1] matches [expr2].
 */
class LikeEscapeOp(expr1: Expression<*>, expr2: Expression<*>, like: Boolean, val escapeChar: Char?) :
    ComparisonOp(expr1, expr2, if (like) "LIKE" else "NOT LIKE") {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        super.toQueryBuilder(queryBuilder)
        if (escapeChar != null) {
            with(queryBuilder) {
                +" ESCAPE "
                +stringParam(escapeChar.toString())
            }
        }
    }
}

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
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.regexp(expr1, expr2, caseSensitive, queryBuilder)
    }
}

// Subquery Expressions

/**
 * Represents an SQL operator that checks if [query] returns at least one row.
 */
class Exists(
    /** Returns the query being checked. */
    val query: AbstractQuery<*>
) : Op<Boolean>(), Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("EXISTS (")
        query.prepareSQL(this)
        append(")")
    }
}

/** Returns an SQL operator that checks if [query] returns at least one row. */
fun exists(query: AbstractQuery<*>) = Exists(query)

/**
 * Represents an SQL operator that checks if [query] doesn't returns any row.
 */
class NotExists(
    /** Returns the query being checked. */
    val query: AbstractQuery<*>
) : Op<Boolean>(), Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("NOT EXISTS (")
        query.prepareSQL(this)
        append(")")
    }
}

/** Returns an SQL operator that checks if [query] doesn't returns any row. */
fun notExists(query: AbstractQuery<*>) = NotExists(query)

/** Represents an SQL operator that compares [expr] to any row returned from [query]. */
sealed class SubQueryOp<T>(
    /** Returns the string representation of the operator to use in the comparison. */
    val operator: String,
    /** Returns the expression compared to each row of the query result. */
    val expr: Expression<T>,
    /** Returns the query to check against. */
    val query: AbstractQuery<*>
) : Op<Boolean>(), ComplexExpression, Op.OpBoolean {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(expr, " $operator (")
        query.prepareSQL(this)
        +")"
    }
}

/**
 * Represents an SQL operator that checks if [expr] is equals to any row returned from [query].
 */
class InSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("IN", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is not equals to any row returned from [query].
 */
class NotInSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("NOT IN", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is equals to single value returned from [query].
 */
class EqSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("=", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is not equals to single value returned from [query].
 */
class NotEqSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("!=", expr, query)

// Literals

/**
 * Represents the specified [value] as an SQL literal, using the specified [columnType] to convert the value.
 */
class LiteralOp<T>(
    override val columnType: IColumnType<T & Any>,
    /** Returns the value being used as a literal. */
    val value: T
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +columnType.valueToString(value) }
}

/** Returns the specified [value] as a boolean literal. */
fun booleanLiteral(value: Boolean): LiteralOp<Boolean> = LiteralOp(BooleanColumnType.INSTANCE, value)

/** Returns the specified [value] as a byte literal. */
fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(ByteColumnType(), value)

/** Returns the specified [value] as a unsigned byte literal. */
fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(UByteColumnType(), value)

/** Returns the specified [value] as a short literal. */
fun shortLiteral(value: Short): LiteralOp<Short> = LiteralOp(ShortColumnType(), value)

/** Returns the specified [value] as a unsigned short literal. */
fun ushortLiteral(value: UShort): LiteralOp<UShort> = LiteralOp(UShortColumnType(), value)

/** Returns the specified [value] as an int literal. */
fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerColumnType(), value)

/** Returns the specified [value] as a unsigned int literal. */
fun uintLiteral(value: UInt): LiteralOp<UInt> = LiteralOp(UIntegerColumnType(), value)

/** Returns the specified [value] as a long literal. */
fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongColumnType(), value)

/** Returns the specified [value] as a unsigned long literal. */
fun ulongLiteral(value: ULong): LiteralOp<ULong> = LiteralOp(ULongColumnType(), value)

/** Returns the specified [value] as a float literal. */
fun floatLiteral(value: Float): LiteralOp<Float> = LiteralOp(FloatColumnType(), value)

/** Returns the specified [value] as a double literal. */
fun doubleLiteral(value: Double): LiteralOp<Double> = LiteralOp(DoubleColumnType(), value)

/** Returns the specified [value] as a string literal. */
fun stringLiteral(value: String): LiteralOp<String> = LiteralOp(TextColumnType(), value)

/** Returns the specified [value] as a decimal literal. */
fun decimalLiteral(value: BigDecimal): LiteralOp<BigDecimal> = LiteralOp(DecimalColumnType(value.precision(), value.scale()), value)

/**
 * Returns the specified [value] as an array literal, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> arrayLiteral(value: List<T>, delegateType: ColumnType<T>? = null): LiteralOp<List<T>> =
    arrayNLiteral(value, delegateType, dimensions = 1)

/**
 * Returns the specified [value] as an array literal, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * **Note:** Because arrays can have varying dimensions, you must specify the type of elements
 * and the number of dimensions when using array literals.
 * For example, use `arrayNLiteral<Int, List<List<Int>>>(list, dimensions = 2)`.
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any, R : List<Any>> arrayNLiteral(value: R, delegateType: ColumnType<T>? = null, dimensions: Int): LiteralOp<R> {
    @OptIn(InternalApi::class)
    return LiteralOp(ArrayColumnType(delegateType ?: resolveColumnType(T::class), dimensions), value)
}

// Query Parameters

/**
 * Represents the specified [value] as a query parameter, using the specified [sqlType] to convert the value.
 */
class QueryParameter<T>(
    /** Returns the value being used as a query parameter. */
    val value: T,
    /** Returns the column type of this expression. */
    val sqlType: IColumnType<T & Any>
) : Expression<T>() {
    internal val compositeValue: CompositeID? = (value as? EntityID<*>)?.value as? CompositeID

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            compositeValue?.let {
                it.values.entries.appendTo { (column, value) ->
                    registerArgument(column.columnType, value)
                }
            } ?: registerArgument(sqlType, value)
        }
    }
}

/** Returns the specified [value] as a query parameter with the same type as [column]. */
fun <T : Comparable<T>> idParam(value: EntityID<T>, column: Column<EntityID<T>>): Expression<EntityID<T>> =
    QueryParameter(value, column.columnType)

/** Returns the specified [value] as a boolean query parameter. */
fun booleanParam(value: Boolean): Expression<Boolean> = QueryParameter(value, BooleanColumnType.INSTANCE)

/** Returns the specified [value] as a byte query parameter. */
fun byteParam(value: Byte): Expression<Byte> = QueryParameter(value, ByteColumnType())

/** Returns the specified [value] as a unsigned byte query parameter. */
fun ubyteParam(value: UByte): Expression<UByte> = QueryParameter(value, UByteColumnType())

/** Returns the specified [value] as a short query parameter. */
fun shortParam(value: Short): Expression<Short> = QueryParameter(value, ShortColumnType())

/** Returns the specified [value] as a unsigned short query parameter. */
fun ushortParam(value: UShort): Expression<UShort> = QueryParameter(value, UShortColumnType())

/** Returns the specified [value] as an int query parameter. */
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())

/** Returns the specified [value] as a unsigned int query parameter. */
fun uintParam(value: UInt): Expression<UInt> = QueryParameter(value, UIntegerColumnType())

/** Returns the specified [value] as a long query parameter. */
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())

/** Returns the specified [value] as a unsigned long query parameter. */
fun ulongParam(value: ULong): Expression<ULong> = QueryParameter(value, ULongColumnType())

/** Returns the specified [value] as a float query parameter. */
fun floatParam(value: Float): Expression<Float> = QueryParameter(value, FloatColumnType())

/** Returns the specified [value] as a double query parameter. */
fun doubleParam(value: Double): Expression<Double> = QueryParameter(value, DoubleColumnType())

/** Returns the specified [value] as a string query parameter. */
fun stringParam(value: String): Expression<String> = QueryParameter(value, TextColumnType())

/** Returns the specified [value] as a decimal query parameter. */
fun decimalParam(value: BigDecimal): Expression<BigDecimal> = QueryParameter(value, DecimalColumnType(value.precision(), value.scale()))

/**
 * Returns the specified [value] as a blob query parameter.
 *
 * Set [useObjectIdentifier] to `true` if the parameter should be processed using an OID column instead of a
 * BYTEA column. This is only supported by PostgreSQL databases.
 */
fun blobParam(value: ExposedBlob, useObjectIdentifier: Boolean = false): Expression<ExposedBlob> =
    QueryParameter(value, BlobColumnType(useObjectIdentifier))

/**
 * Returns the specified [value] as an array query parameter, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> arrayParam(value: List<T>, delegateType: ColumnType<T>? = null): Expression<List<T>> =
    arrayNParam(value, delegateType, dimensions = 1)

/**
 * Returns the specified [value] as an array query parameter, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * **Note:** Because arrays can have varying dimensions, you must specify the type of elements
 * and the number of dimensions when using array literals.
 * For example, use `arrayNParam<Int, List<List<Int>>>(list, dimensions = 2)`.
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any, R : List<Any>> arrayNParam(value: R, delegateType: ColumnType<T>? = null, dimensions: Int): Expression<R> {
    @OptIn(InternalApi::class)
    return QueryParameter(value, ArrayColumnType(delegateType ?: resolveColumnType(T::class), dimensions))
}

// Misc.

/**
 * Represents an SQL operator that doesn't perform any operation.
 * This is mainly used to change between column types.
 */
class NoOpConversion<T, S>(
    /** Returns the expression whose type is being changed. */
    val expr: Expression<T>,
    override val columnType: IColumnType<S & Any>
) : ExpressionWithColumnType<S>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +expr }
}

/** Appends an expression that is wrapped in parentheses (if necessary by [ComplexExpression]). */
private fun QueryBuilder.appendExpression(expr: Expression<*>) {
    if (expr is ComplexExpression) {
        append("(", expr, ")")
    } else {
        append(expr)
    }
}

/**
 * Appends a comparison string between [expr1] and [expr2] using the given SQL [op], for when an operator class
 * cannot directly extend the [ComparisonOp] class.
 */
private fun QueryBuilder.appendComparison(expr1: Expression<*>, expr2: Expression<*>, op: String) {
    appendExpression(expr1)
    +" $op "
    appendExpression(expr2)
}
