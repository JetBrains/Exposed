package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.h2Mode
import java.math.BigDecimal

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
 * Represents an SQL function that concatenates the text representation of all non-null input values of each group
 * from [expr], separated by [separator].
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
class Min<T : Comparable<T>, in S : T?>(
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
class Max<T : Comparable<T>, in S : T?>(
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
 * Represents an SQL function that allows the comparison of [value] to chained conditional clauses.
 *
 * If [value] is not provided, each chained conditional will be evaluated independently.
 */
@Suppress("FunctionNaming")
class Case(
    /** The value that is compared against every conditional expression. */
    val value: Expression<*>? = null
) {
    /** Adds a conditional expression with a [result] if the expression evaluates to `true`. */
    fun <T> When(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> = CaseWhen<T>(value).When(cond, result)
}

/**
 * Represents an SQL function that allows the comparison of [value] to chained conditional clauses.
 *
 * If [value] is not provided, each chained conditional will be evaluated independently.
 */
@Suppress("FunctionNaming")
class CaseWhen<T>(
    /** The value that is compared against every conditional expression. */
    val value: Expression<*>?
) {
    /** The boolean conditions to check and their resulting expressions if the condition is met. */
    val cases: MutableList<Pair<Expression<Boolean>, Expression<out T>>> = mutableListOf()

    /** Adds a conditional expression with a [result] if the expression evaluates to `true`. */
    fun When(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> {
        cases.add(cond to result)
        return this
    }

    /** Adds an expression that will be used as the function result if all [cases] evaluate to `false`. */
    fun Else(e: Expression<T>): ExpressionWithColumnType<T> = CaseWhenElse(this, e)
}

/**
 * Represents an SQL function that steps through conditions, and either returns a value when the first condition is met
 * or returns [elseResult] if all conditions are `false`.
 */
class CaseWhenElse<T>(
    /** The conditions to check and their results if met. */
    val caseWhen: CaseWhen<T>,
    /** The result if none of the conditions checked are found to be `true`. */
    val elseResult: Expression<T>
) : ExpressionWithColumnType<T>(), ComplexExpression {

    override val columnType: IColumnType<T & Any> =
        (elseResult as? ExpressionWithColumnType<T>)?.columnType
            ?: caseWhen.cases.map { it.second }.filterIsInstance<ExpressionWithColumnType<T>>().firstOrNull()?.columnType
            ?: error("No column type has been found")

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append("CASE")
            if (caseWhen.value != null) {
                +" "
                +caseWhen.value
            }

            for ((first, second) in caseWhen.cases) {
                append(" WHEN ", first, " THEN ", second)
            }

            append(" ELSE ", elseResult, " END")
        }
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
