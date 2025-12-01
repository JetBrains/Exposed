package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.vendors.*
import java.math.BigDecimal

/**
 * Represents an SQL operator.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Op<T> : Expression<T>() {
    companion object {
        @Deprecated(
            message = "This builder method will continue to be phased out following release 1.0.0 and should be replaced " +
                "with the contents of the lambda block pulled out of the parentheses. The `SqlExpressionBuilder` receiver " +
                "has been deprecated, as well as all expression builder methods previously restricted to the object, " +
                "in favor of equivalent top-level functions, making this scope function redundant. " +
                "It will no longer be necessary to import each individual method when used outside a scoped block, " +
                "and on demand imports will now be possible via 'import org.jetbrains.exposed.v1.core.*', if required. " +
                "",
            replaceWith = ReplaceWith("op()"),
            level = DeprecationLevel.ERROR
        )
        inline fun <T> build(op: () -> Op<T>): Op<T> = op()

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
                    (booleanLiteral(true) eq booleanLiteral(true)).toQueryBuilder(this)

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
                    (booleanLiteral(true) eq booleanLiteral(false)).toQueryBuilder(this)
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

// Bitwise Operators

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
        when (currentDialectIfAvailable) {
            is OracleDialect -> append("BITAND(", expr1, ", ", expr2, ")")
            is H2Dialect -> {
                +"BITAND("
                castToExpressionTypeForH2BitWiseIps(expr1, this)
                +", "
                castToExpressionTypeForH2BitWiseIps(expr2, this)
                +")"
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
        when (currentDialectIfAvailable) {
            // Oracle doesn't natively support bitwise OR, thus emulate it with 'and'
            is OracleDialect -> append("(", expr1, "+", expr2, "-", AndBitOp(expr1, expr2, columnType), ")")
            is H2Dialect -> {
                +"BITOR("
                castToExpressionTypeForH2BitWiseIps(expr1, this)
                +", "
                castToExpressionTypeForH2BitWiseIps(expr2, this)
                +")"
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
        when (currentDialectIfAvailable) {
            // Oracle and SQLite don't natively support bitwise XOR, thus emulate it with 'or' and 'and'
            is OracleDialect, is SQLiteDialect -> append(
                "(", OrBitOp(expr1, expr2, columnType), "-", AndBitOp(expr1, expr2, columnType), ")"
            )
            is PostgreSQLDialect -> append('(', expr1, " # ", expr2, ')')
            is H2Dialect -> {
                +"BITXOR("
                castToExpressionTypeForH2BitWiseIps(expr1, this)
                +", "
                castToExpressionTypeForH2BitWiseIps(expr2, this)
                +")"
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

/**
 * Represents an SQL operator that checks if [expr] is less than the single value returned from [query].
 */
class LessSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("<", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is less than or equal to the single value returned from [query].
 */
class LessEqSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>("<=", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is greater than the single value returned from [query].
 */
class GreaterSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>(">", expr, query)

/**
 * Represents an SQL operator that checks if [expr] is greater than or equal to the single value returned from [query].
 */
class GreaterEqSubQueryOp<T>(expr: Expression<T>, query: AbstractQuery<*>) : SubQueryOp<T>(">=", expr, query)

// Value Operators

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

/** Represents a pattern used for the comparison of string expressions. */
data class LikePattern(
    /** The string representation of a pattern to match. */
    val pattern: String,
    /** The special character to use as the escape character. */
    val escapeChar: Char? = null
) {

    infix operator fun plus(rhs: LikePattern): LikePattern {
        require(escapeChar == rhs.escapeChar) { "Mixing escape chars '$escapeChar' vs. '${rhs.escapeChar} is not allowed" }
        return LikePattern(pattern + rhs.pattern, rhs.escapeChar)
    }

    infix operator fun plus(rhs: String): LikePattern {
        return LikePattern(pattern + rhs, escapeChar)
    }

    companion object {
        /** Creates a [LikePattern] from the provided [text], with any special characters escaped using [escapeChar]. */
        fun ofLiteral(text: String, escapeChar: Char = '\\'): LikePattern {
            val likePatternSpecialChars = currentDialect.likePatternSpecialChars
            val nextExpectedPatternQueue = arrayListOf<Char>()
            var nextCharToEscape: Char? = null
            val escapedPattern = buildString {
                text.forEach {
                    val shouldEscape = when (it) {
                        escapeChar -> true
                        in likePatternSpecialChars -> {
                            likePatternSpecialChars[it]?.let { nextChar ->
                                nextExpectedPatternQueue.add(nextChar)
                                nextCharToEscape = nextChar
                            }
                            true
                        }
                        nextCharToEscape -> {
                            nextExpectedPatternQueue.removeLast()
                            nextCharToEscape = nextExpectedPatternQueue.lastOrNull()
                            true
                        }
                        else -> false
                    }
                    if (shouldEscape) {
                        append(escapeChar)
                    }
                    append(it)
                }
            }
            return LikePattern(escapedPattern, escapeChar)
        }
    }
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
