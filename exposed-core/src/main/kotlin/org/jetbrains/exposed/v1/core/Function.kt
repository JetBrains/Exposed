package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.H2FunctionProvider
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import java.math.BigDecimal
import kotlin.collections.filterIsInstance

/**
 * Represents an SQL function.
 */
abstract class Function<T>(override val columnType: IColumnType<T & Any>) : ExpressionWithColumnType<T>()

/**
 * Represents a custom SQL function.
 */
open class CustomFunction<T>(
    /** Returns the name of the function. */
    val functionName: String,
    columnType: IColumnType<T & Any>,
    /** Returns the list of arguments of this function. */
    vararg val expr: Expression<*>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(functionName, '(')
        expr.appendTo { +it }
        append(')')
    }
}

/**
 * Represents a custom SQL binary operator.
 */
open class CustomOperator<T>(
    /** Returns the name of the operator. */
    val operatorName: String,
    columnType: IColumnType<T & Any>,
    /** Returns the left-hand side operand. */
    val expr1: Expression<*>,
    /** Returns the right-hand side operand. */
    val expr2: Expression<*>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append('(', expr1, ' ', operatorName, ' ', expr2, ')')
    }
}

// Mathematical Functions

/**
 * Represents an SQL function that returns a random value in the range 0.0 <= x < 1.0, using the specified [seed].
 *
 * **Note:** Some vendors generate values outside this range, or ignore the given seed, check the documentation.
 */
class Random(
    /** Returns the seed. */
    val seed: Int? = null
) : Function<BigDecimal>(DecimalColumnType(precision = 38, scale = 20)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        val functionProvider = when (currentDialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.Oracle, H2Dialect.H2CompatibilityMode.SQLServer -> H2FunctionProvider
            else -> currentDialect.functionProvider
        }
        +functionProvider.random(seed)
    }
}

// String Functions

/**
 * Represents an SQL function that returns the length of [expr], measured in characters, or `null` if [expr] is null.
 */
class CharLength<T : String?>(
    val expr: Expression<T>
) : Function<Int?>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.charLength(expr, queryBuilder)
    }
}

/**
 * Represents an SQL function that converts [expr] to lower case.
 */
class LowerCase<T : String?>(
    /** Returns the expression to convert. */
    val expr: Expression<T>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("LOWER(", expr, ")") }
}

/**
 * Represents an SQL function that converts [expr] to upper case.
 */
class UpperCase<T : String?>(
    /** Returns the expression to convert. */
    val expr: Expression<T>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("UPPER(", expr, ")") }
}

/**
 * Represents an SQL function that concatenates the text representations of all non-null input values from [expr], separated by [separator].
 */
class Concat(
    /** Returns the delimiter. */
    val separator: String,
    /** Returns the expressions being concatenated. */
    vararg val expr: Expression<*>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.concat(separator, queryBuilder, expr = expr)
    }
}

/**
 * Represents an SQL function that concatenates the text representation of all non-null input values of each group from [expr], separated by [separator]
 */
class GroupConcat<T : String?>(
    /** Returns grouped expression being concatenated. */
    val expr: Expression<T>,
    /** Returns the delimiter. */
    val separator: String?,
    /** Returns `true` if only distinct elements are concatenated, `false` otherwise. */
    val distinct: Boolean,
    /** Returns the order in which the elements of each group are sorted. */
    vararg val orderBy: Pair<Expression<*>, SortOrder>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.groupConcat(this, queryBuilder)
    }
}

/**
 * Represents an SQL function that extract a substring from [expr] that begins at the specified [start] and with the specified [length].
 */
class Substring<T : String?>(
    private val expr: Expression<T>,
    private val start: Expression<Int>,
    /** Returns the length of the substring. */
    val length: Expression<Int>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.substring(expr, start, length, queryBuilder)
    }
}

/**
 * Represents an SQL function that remove the longest string containing only spaces from both ends of [expr]
 */
class Trim<T : String?>(
    /** Returns the expression being trimmed. */
    val expr: Expression<T>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("TRIM(", expr, ")") }
}

/**
 * Represents an SQL function that returns the index of the first occurrence of [substring] in [expr] or 0
 */
class Locate<T : String?>(val expr: Expression<T>, val substring: String) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        currentDialect.functionProvider.locate(queryBuilder, expr, substring)
}

// General-Purpose Aggregate Functions

/**
 * Represents an SQL function that returns the minimum value of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Min<T : Any, in S : T?>(
    /** Returns the expression from which the minimum value is obtained. */
    val expr: Expression<in S>,
    columnType: IColumnType<T>
) : Function<T?>(columnType), WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("MIN(", expr, ")") }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the maximum value of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Max<T : Any, in S : T?>(
    /** Returns the expression from which the maximum value is obtained. */
    val expr: Expression<in S>,
    columnType: IColumnType<T>
) : Function<T?>(columnType), WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("MAX(", expr, ")") }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the average (arithmetic mean) of all non-null input values, or `null` if there are no non-null values.
 */
class Avg<T : Comparable<T>, S : T?>(
    /** Returns the expression from which the average is calculated. */
    val expr: Expression<S>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)), WindowFunction<BigDecimal?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("AVG(", expr, ")") }

    override fun over(): WindowFunctionDefinition<BigDecimal?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the sum of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Sum<T>(
    /** Returns the expression from which the sum is calculated. */
    val expr: Expression<T>,
    columnType: IColumnType<T & Any>
) : Function<T?>(columnType), WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("SUM(", expr, ")") }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the number of input rows for which the value of [expr] is not null.
 */
class Count(
    /** Returns the expression from which the rows are counted. */
    val expr: Expression<*>,
    /** Returns whether only distinct element should be count. */
    val distinct: Boolean = false
) : Function<Long>(LongColumnType()), WindowFunction<Long> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"COUNT("
        if (distinct) +"DISTINCT "
        +expr
        +")"
    }

    override fun over(): WindowFunctionDefinition<Long> {
        return WindowFunctionDefinition(LongColumnType(), this)
    }
}

// Aggregate Functions for Statistics

/**
 * Represents an SQL function that returns the population standard deviation of the non-null input values,
 * or `null` if there are no non-null values.
 */
class StdDevPop<T>(
    /** Returns the expression from which the population standard deviation is calculated. */
    val expression: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)), WindowFunction<BigDecimal?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            val functionProvider = when (currentDialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.SQLServer -> H2FunctionProvider
                else -> currentDialect.functionProvider
            }
            functionProvider.stdDevPop(expression, this)
        }
    }

    override fun over(): WindowFunctionDefinition<BigDecimal?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the sample standard deviation of the non-null input values,
 * or `null` if there are no non-null values.
 */
class StdDevSamp<T>(
    /** Returns the expression from which the sample standard deviation is calculated. */
    val expression: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)), WindowFunction<BigDecimal?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            val functionProvider = when (currentDialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.SQLServer -> H2FunctionProvider
                else -> currentDialect.functionProvider
            }
            functionProvider.stdDevSamp(expression, this)
        }
    }

    override fun over(): WindowFunctionDefinition<BigDecimal?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the population variance of the non-null input values (square of the population standard deviation),
 * or `null` if there are no non-null values.
 */
class VarPop<T>(
    /** Returns the expression from which the population variance is calculated. */
    val expression: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)), WindowFunction<BigDecimal?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            val functionProvider = when (currentDialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.SQLServer -> H2FunctionProvider
                else -> currentDialect.functionProvider
            }
            functionProvider.varPop(expression, this)
        }
    }

    override fun over(): WindowFunctionDefinition<BigDecimal?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

/**
 * Represents an SQL function that returns the sample variance of the non-null input values (square of the sample standard deviation),
 * or `null` if there are no non-null values.
 */
class VarSamp<T>(
    /** Returns the expression from which the sample variance is calculated. */
    val expression: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)), WindowFunction<BigDecimal?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            val functionProvider = when (currentDialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.SQLServer -> H2FunctionProvider
                else -> currentDialect.functionProvider
            }
            functionProvider.varSamp(expression, this)
        }
    }

    override fun over(): WindowFunctionDefinition<BigDecimal?> {
        return WindowFunctionDefinition(columnType, this)
    }
}

// Sequence Manipulation Functions

/**
 * Represents an SQL function that advances the specified [seq] and returns the new value.
 */
sealed class NextVal<T>(
    /** Returns the sequence from which the next value is obtained. */
    val seq: Sequence,
    columnType: IColumnType<T & Any>
) : Function<T>(columnType) {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.nextVal(seq, queryBuilder)
    }

    class IntNextVal(seq: Sequence) : NextVal<Int>(seq, IntegerColumnType())
    class LongNextVal(seq: Sequence) : NextVal<Long>(seq, LongColumnType())
}

// Conditional Expressions

/**
 * Base abstract class for SQL CASE expressions that provide conditional logic in queries.
 *
 * @param T The return type of the CASE expression
 */
abstract class BaseCaseWhen<T> : ExpressionWithColumnType<T>(), ComplexExpression {
    /** List of condition-result pairs that define the WHEN clauses */
    abstract val cases: List<Pair<Expression<*>, Expression<out T>>>

    /** Optional value to compare against in the CASE statement (for value-based CASE expressions) */
    open val value: Expression<*>? = null

    /** Optional ELSE result expression that is used when no WHEN conditions match */
    open val elseResult: Expression<out T>? = null

    /** Returns all result expressions from the WHEN and ELSE clauses */
    abstract fun expressions(): List<Expression<out T>>

    /**
     * Determines the column type by examining the result expressions.
     */
    @Suppress("UNCHECKED_CAST")
    override val columnType: IColumnType<T & Any>
        get() = expressions().filterIsInstance<ExpressionWithColumnType<T>>().firstOrNull()?.columnType
            ?: expressions().filterIsInstance<Op.OpBoolean>().firstOrNull()?.let { BooleanColumnType.INSTANCE as IColumnType<T & Any> }
            ?: error("No column type has been found")

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append("CASE")
            val caseValue = value
            if (caseValue != null) {
                +" "
                +caseValue
            }

            for ((first, second) in cases) {
                append(" WHEN ", first, " THEN ", second)
            }

            elseResult?.let {
                append(" ELSE ", it)
            }

            append(" END")
        }
    }
}

/**
 * Builder class for creating value-based CASE expressions where a specific value is compared
 * against different conditions.
 *
 * @param T The type of the value being compared
 * @param value The expression whose value will be compared in WHEN clauses
 */
@Suppress("FunctionNaming")
class ValueCase<T>(
    val value: ExpressionWithColumnType<T>,
) {
    /**
     * Adds a WHEN clause that compares the case value against a literal condition.
     *
     * @param R The return type of the result expression
     * @param cond The literal value to compare against
     * @param result The expression to return if the condition matches
     * @return A ValueCaseWhen instance for method chaining
     */
    fun <R> When(cond: T, result: Expression<R>): ValueCaseWhen<T, R> {
        return ValueCaseWhen<T, R>(value).When(cond, result)
    }

    /**
     * Adds a WHEN clause that compares the case value against an expression condition.
     *
     * @param R The return type of the result expression
     * @param cond The expression to compare against
     * @param result The expression to return if the condition matches
     * @return A ValueCaseWhen instance for method chaining
     */
    fun <R> When(cond: Expression<T>, result: Expression<R>): ValueCaseWhen<T, R> {
        return ValueCaseWhen<T, R>(value).When(cond, result)
    }

    /**
     * Adds a WHEN clause that compares the case value against an expression condition with a literal result.
     *
     * @param R The return type of the result value
     * @param cond The expression to compare against
     * @param result The literal value to return if the condition matches
     * @param resultType Optional column type for the result value
     * @return A ValueCaseWhen instance for method chaining
     */
    fun <R> When(cond: Expression<T>, result: R, resultType: IColumnType<R & Any>? = null): ValueCaseWhen<T, R> {
        return ValueCaseWhen<T, R>(value).When(cond, result, resultType)
    }

    /**
     * Adds a WHEN clause that compares the case value against a literal condition with a literal result.
     *
     * @param R The return type of the result value
     * @param cond The literal value to compare against
     * @param result The literal value to return if the condition matches
     * @param resultType Optional column type for the result value
     * @return A ValueCaseWhen instance for method chaining
     */
    fun <R> When(cond: T, result: R, resultType: IColumnType<R & Any>? = null): ValueCaseWhen<T, R> {
        return ValueCaseWhen<T, R>(value).When(cond, result, resultType)
    }
}

/**
 * Represents a value-based CASE expression that can be extended with additional WHEN clauses
 * or completed with an ELSE clause.
 *
 * @param T The type of the value being compared
 * @param R The return type of the CASE expression
 * @param value The expression whose value will be compared in WHEN clauses
 */
@Suppress("FunctionNaming")
class ValueCaseWhen<T, R>(
    override val value: ExpressionWithColumnType<T>,
) : BaseCaseWhen<R?>() {
    /** Mutable list of condition-result pairs for WHEN clauses */
    override val cases: MutableList<Pair<Expression<T>, Expression<out R>>> = mutableListOf()

    /**
     * Adds a WHEN clause that compares against an expression condition.
     *
     * @param cond The expression to compare the case value against
     * @param result The expression to return if the condition matches
     * @return This ValueCaseWhen instance for method chaining
     */
    fun When(cond: Expression<T>, result: Expression<R>): ValueCaseWhen<T, R> {
        cases.add(cond to result)
        return this
    }

    /**
     * Adds a WHEN clause that compares against a literal condition.
     *
     * @param cond The literal value to compare the case value against
     * @param result The expression to return if the condition matches
     * @return This ValueCaseWhen instance for method chaining
     */
    fun When(cond: T, result: Expression<R>): ValueCaseWhen<T, R> {
        return When(QueryParameter(cond, value.columnType), result)
    }

    /**
     * Adds a WHEN clause that compares against an expression condition with a literal result.
     *
     * @param cond The expression to compare the case value against
     * @param result The literal value to return if the condition matches
     * @param resultType Optional column type for the result value
     * @return This ValueCaseWhen instance for method chaining
     */
    fun When(cond: Expression<T>, result: R, resultType: IColumnType<R & Any>? = null): ValueCaseWhen<T, R> {
        return When(cond, QueryParameter(result, resultType ?: columnType))
    }

    /**
     * Adds a WHEN clause that compares against a literal condition with a literal result.
     *
     * @param cond The literal value to compare the case value against
     * @param result The literal value to return if the condition matches
     * @param resultType Optional column type for the result value
     * @return This ValueCaseWhen instance for method chaining
     */
    fun When(cond: T, result: R, resultType: IColumnType<R & Any>? = null): ValueCaseWhen<T, R> {
        return When(QueryParameter(cond, value.columnType), result, resultType)
    }

    /**
     * Adds an ELSE clause with an expression result, completing the CASE statement.
     *
     * @param result The expression to return if no WHEN conditions match
     * @return A completed ValueCaseWhenElse instance
     */
    fun Else(result: Expression<R>): ValueCaseWhenElse<T, R> {
        return ValueCaseWhenElse(value, cases, result)
    }

    /**
     * Adds an ELSE clause with a literal result, completing the CASE statement.
     *
     * @param result The literal value to return if no WHEN conditions match
     * @param resultType Optional column type for the result value
     * @return A completed ValueCaseWhenElse instance
     */
    fun Else(result: R, resultType: IColumnType<R & Any>? = null): ValueCaseWhenElse<T, R> {
        return ValueCaseWhenElse(value, cases, QueryParameter(result, resultType ?: columnType))
    }

    /** Returns all result expressions from the WHEN clauses */
    override fun expressions(): List<Expression<out R>> {
        return cases.map { it.second }
    }
}

/**
 * Represents a completed value-based CASE expression with an ELSE clause.
 *
 * @param T The type of the value being compared
 * @param R The return type of the CASE expression
 * @param value The expression whose value is compared in WHEN clauses
 * @param cases The list of condition-result pairs for WHEN clauses
 * @param elseResult The result expression for the ELSE clause
 */
class ValueCaseWhenElse<T, R>(
    override val value: Expression<T>,
    override val cases: List<Pair<Expression<T>, Expression<out R>>>,
    override val elseResult: Expression<out R>
) : BaseCaseWhen<R>() {
    /** Returns all result expressions from both WHEN clauses and the ELSE clause */
    override fun expressions(): List<Expression<out R>> {
        return cases.map { it.second } + elseResult
    }
}

/**
 * Builder class for creating conditional CASE expressions where each WHEN clause
 * contains an independent boolean condition.
 */
@Suppress("FunctionNaming")
class Case {
    /**
     * Adds a conditional expression with a result if the condition evaluates to true.
     *
     * @param T The return type of the result expression
     * @param cond The boolean condition to evaluate
     * @param result The expression to return if the condition is true
     * @return A CaseWhen instance for method chaining
     */
    fun <T> When(cond: Expression<*>, result: Expression<T>): CaseWhen<T> = CaseWhen<T>().When(cond, result)
}

/**
 * Represents a conditional CASE expression that can be extended with additional WHEN clauses
 * or completed with an ELSE clause.
 *
 * @param T The return type of the CASE expression
 */
@Suppress("FunctionNaming")
class CaseWhen<T> : BaseCaseWhen<T?>() {
    /** The boolean conditions to check and their resulting expressions if the condition is met */
    override val cases: MutableList<Pair<Expression<*>, Expression<out T>>> = mutableListOf()

    /**
     * Adds a conditional expression with a result if the condition evaluates to true.
     *
     * @param cond The boolean condition to evaluate
     * @param result The expression to return if the condition is true
     * @return This CaseWhen instance for method chaining
     */
    fun When(cond: Expression<*>, result: Expression<T>): CaseWhen<T> {
        cases.add(cond to result)
        return this
    }

    /**
     * Adds a conditional expression with a literal result if the condition evaluates to true.
     *
     * @param cond The boolean condition to evaluate
     * @param result The literal value to return if the condition is true
     * @param resultType Optional column type for the result value
     * @return This CaseWhen instance for method chaining
     */
    fun When(cond: Expression<T>, result: T, resultType: IColumnType<T & Any>? = null): CaseWhen<T> {
        return When(cond, QueryParameter(result, resultType ?: columnType))
    }

    /** Returns all result expressions from the WHEN clauses */
    override fun expressions(): List<Expression<out T?>> {
        return cases.map { it.second }
    }

    /**
     * Adds an ELSE clause with an expression result, completing the CASE statement.
     *
     * @param e The expression to return if all conditions are false
     * @return A completed CaseWhenElse instance
     */
    fun Else(e: Expression<T>): ExpressionWithColumnType<T> = CaseWhenElse(cases, e)

    /**
     * Adds an ELSE clause with a literal result, completing the CASE statement.
     *
     * @param result The literal value to return if all conditions are false
     * @param resultType Optional column type for the result value
     * @return A completed CaseWhenElse instance
     */
    fun Else(result: T, resultType: IColumnType<T & Any>? = null): CaseWhenElse<T> {
        return CaseWhenElse(cases, QueryParameter(result, resultType ?: columnType))
    }
}

/**
 * Represents a completed conditional CASE expression with an ELSE clause.
 * Steps through conditions and returns a value when the first condition is met,
 * or returns the ELSE result if all conditions are false.
 *
 * @param T The return type of the CASE expression
 * @param cases The conditions to check and their results if met
 * @param elseResult The result if none of the conditions are found to be true
 */
class CaseWhenElse<T>(
    override val cases: List<Pair<Expression<*>, Expression<out T>>>,
    override val elseResult: Expression<T>
) : BaseCaseWhen<T>() {

    /** Returns all result expressions from both WHEN clauses and the ELSE clause */
    override fun expressions(): List<Expression<out T>> {
        return cases.map { it.second } + elseResult
    }
}

/**
 * Represents an SQL function that returns the first of its arguments that is not null.
 */
@Suppress("UNCHECKED_CAST")
class Coalesce<T, S : T?>(
    private val expr: ExpressionWithColumnType<S>,
    private val alternate: Expression<out T>,
    private vararg val others: Expression<out T>
) : Function<T>(expr.columnType as IColumnType<T & Any>) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        (listOf(expr, alternate) + others).appendTo(
            prefix = "COALESCE(",
            postfix = ")",
            separator = ", "
        ) { +it }
    }
}

// Value Expressions

/**
 * Represents an SQL function that specifies a conversion from one data type to another.
 */
class Cast<T>(
    /** Returns the expression being casted. */
    val expr: Expression<*>,
    columnType: IColumnType<T & Any>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
    }
}
