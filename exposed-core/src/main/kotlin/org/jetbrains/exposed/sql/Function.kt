package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal

/**
 * Represents an SQL function.
 */
abstract class Function<T>(override val columnType: IColumnType) : ExpressionWithColumnType<T>()

/**
 * Represents a custom SQL function.
 */
open class CustomFunction<T>(
    /** Returns the name of the function. */
    val functionName: String,
    _columnType: IColumnType,
    /** Returns the list of arguments of this function. */
    vararg val expr: Expression<*>
) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(functionName, '(')
        expr.toList().appendTo { +it }
        append(')')
    }
}

/**
 * Represents a custom SQL binary operator.
 */
open class CustomOperator<T>(
    /** Returns the name of the operator. */
    val operatorName: String,
    _columnType: IColumnType,
    /** Returns the left-hand side operand. */
    val expr1: Expression<*>,
    /** Returns the right-hand side operand. */
    val expr2: Expression<*>
) : Function<T>(_columnType) {
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
) : Function<BigDecimal>(DecimalColumnType(38, 20)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +currentDialect.functionProvider.random(seed) }
}


// String Functions

/**
 * Represents an SQL function that converts [expr] to lower case.
 */
class LowerCase<T : String?>(
    /** Returns the expression to convert. */
    val expr: Expression<T>
) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("LOWER(", expr, ")") }
}

/**
 * Represents an SQL function that converts [expr] to upper case.
 */
class UpperCase<T : String?>(
    /** Returns the expression to convert. */
    val expr: Expression<T>
) : Function<T>(VarCharColumnType()) {
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
) : Function<String>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = currentDialect.functionProvider.concat(separator, queryBuilder, *expr)
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
) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = currentDialect.functionProvider.groupConcat(this, queryBuilder)
}

/**
 * Represents an SQL function that extract a substring from [expr] that begins at the specified [start] and with the specified [length].
 */
class Substring<T : String?>(
    private val expr: Expression<T>,
    private val start: Expression<Int>,
    /** Returns the length of the substring. */
    val length: Expression<Int>
) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = currentDialect.functionProvider.substring(expr, start, length, queryBuilder)
}

/**
 * Represents an SQL function that remove the longest string containing only spaces from both ends of [expr]
 */
class Trim<T : String?>(
    /** Returns the expression being trimmed. */
    val expr: Expression<T>
) : Function<T>(VarCharColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("TRIM(", expr, ")") }
}


// General-Purpose Aggregate Functions

/**
 * Represents an SQL function that returns the minimum value of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Min<T : Comparable<T>, in S : T?>(
    /** Returns the expression from which the minimum value is obtained. */
    val expr: Expression<in S>,
    _columnType: IColumnType
) : Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("MIN(", expr, ")") }
}

/**
 * Represents an SQL function that returns the maximum value of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Max<T : Comparable<T>, in S : T?>(
    /** Returns the expression from which the maximum value is obtained. */
    val expr: Expression<in S>,
    _columnType: IColumnType
) : Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("MAX(", expr, ")") }
}

/**
 * Represents an SQL function that returns the average (arithmetic mean) of all non-null input values, or `null` if there are no non-null values.
 */
class Avg<T : Comparable<T>, in S : T?>(
    /** Returns the expression from which the average is calculated. */
    val expr: Expression<in S>, scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("AVG(", expr, ")") }
}

/**
 * Represents an SQL function that returns the sum of [expr] across all non-null input values, or `null` if there are no non-null values.
 */
class Sum<T>(
    /** Returns the expression from which the sum is calculated. */
    val expr: Expression<T>,
    _columnType: IColumnType
) : Function<T?>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("SUM(", expr, ")") }
}

/**
 * Represents an SQL function that returns the number of input rows for which the value of [expr] is not null.
 */
class Count(
    /** Returns the expression from which the rows are counted. */
    val expr: Expression<*>,
    /** Returns whether only distinct element should be count. */
    val distinct: Boolean = false
) : Function<Long>(LongColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"COUNT("
        if (distinct) +"DISTINCT "
        +expr
        +")"
    }
}


// Aggregate Functions for Statistics

/**
 * Represents an SQL function that returns the population standard deviation of the non-null input values,
 * or `null` if there are no non-null values.
 */
class StdDevPop<T>(
    /** Returns the expression from which the population standard deviation is calculated. */
    val expr: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("STDDEV_POP(", expr, ")") }
}

/**
 * Represents an SQL function that returns the sample standard deviation of the non-null input values,
 * or `null` if there are no non-null values.
 */
class StdDevSamp<T>(
    /** Returns the expression from which the sample standard deviation is calculated. */
    val expr: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("STDDEV_SAMP(", expr, ")") }
}

/**
 * Represents an SQL function that returns the population variance of the non-null input values (square of the population standard deviation),
 * or `null` if there are no non-null values.
 */
class VarPop<T>(
    /** Returns the expression from which the population variance is calculated. */
    val expr: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("VAR_POP(", expr, ")") }
}

/**
 * Represents an SQL function that returns the sample variance of the non-null input values (square of the sample standard deviation),
 * or `null` if there are no non-null values.
 */
class VarSamp<T>(
    /** Returns the expression from which the sample variance is calculated. */
    val expr: Expression<T>,
    scale: Int
) : Function<BigDecimal?>(DecimalColumnType(Int.MAX_VALUE, scale)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("VAR_SAMP(", expr, ")") }
}


// Sequence Manipulation Functions

/**
 * Represents an SQL function that advances the specified [seq] and returns the new value.
 */
sealed class NextVal<T> (
    /** Returns the sequence from which the next value is obtained. */
    val seq: Sequence,
    columnType: IColumnType
) : Function<T>(columnType) {

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = currentDialect.functionProvider.nextVal(seq, queryBuilder)

    class IntNextVal(seq: Sequence) : NextVal<Int>(seq, IntegerColumnType())
    class LongNextVal(seq: Sequence) : NextVal<Long>(seq, LongColumnType())
}


// Conditional Expressions

class Case(val value: Expression<*>? = null) {
    fun <T> When(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> = CaseWhen<T>(value).When(cond, result)
}

class CaseWhen<T>(val value: Expression<*>?) {
    val cases: MutableList<Pair<Expression<Boolean>, Expression<out T>>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    fun <R : T> When(cond: Expression<Boolean>, result: Expression<R>): CaseWhen<R> {
        cases.add(cond to result)
        return this as CaseWhen<R>
    }

    fun <R : T> Else(e: Expression<R>): Expression<R> = CaseWhenElse(this, e)
}

class CaseWhenElse<T, R : T>(val caseWhen: CaseWhen<T>, val elseResult: Expression<R>) : Expression<R>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("CASE ")
        if (caseWhen.value != null)
            +caseWhen.value

        for ((first, second) in caseWhen.cases) {
            append(" WHEN ", first, " THEN ", second)
        }

        append(" ELSE ", elseResult, " END")
    }
}

/**
 * Represents an SQL function that returns the first of its arguments that is not null.
 */
class Coalesce<out T, S : T?, R : T>(
    private val expr: ExpressionWithColumnType<S>,
    private val alternate: ExpressionWithColumnType<out T>
) : Function<R>(alternate.columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { append("COALESCE(", expr, ", ", alternate, ")") }
}


// Value Expressions

/**
 * Represents an SQL function that specifies a conversion from one data type to another.
 */
class Cast<T>(
    /** Returns the expression being casted. */
    val expr: Expression<*>,
    columnType: IColumnType
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = currentDialect.functionProvider.cast(expr, columnType, queryBuilder)
}
