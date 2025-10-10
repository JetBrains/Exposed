package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.functions.array.ArrayGet
import org.jetbrains.exposed.v1.core.functions.array.ArraySlice

// General purpose functions

/**
 * Calls a custom SQL function with the specified [functionName] and passes this expression as its only argument.
 */
fun <T> ExpressionWithColumnType<T>.function(functionName: String): CustomFunction<T?> = CustomFunction(functionName, columnType, this)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a string, and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomStringFunction(
    functionName: String,
    vararg params: Expression<*>
): CustomFunction<String?> = CustomFunction(functionName, TextColumnType(), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a long, and passing [params] as its arguments.
 */
@Suppress("FunctionNaming")
fun CustomLongFunction(
    functionName: String,
    vararg params: Expression<*>
): CustomFunction<Long?> = CustomFunction(functionName, LongColumnType(), *params)

// String Functions

/** Returns the length of this string expression, measured in characters, or `null` if this expression is null. */
fun <T : String?> Expression<T>.charLength(): CharLength<T> = CharLength(this)

/** Converts this string expression to lower case. */
fun <T : String?> Expression<T>.lowerCase(): LowerCase<T> = LowerCase(this)

/** Converts this string expression to upper case. */
fun <T : String?> Expression<T>.upperCase(): UpperCase<T> = UpperCase(this)

/** Concatenates the text representations of all the [expr]. */
fun concat(vararg expr: Expression<*>): Concat = Concat("", *expr)

/** Concatenates the text representations of all the [expr] using the specified [separator]. */
fun concat(separator: String = "", expr: List<Expression<*>>): Concat = Concat(separator, expr = expr.toTypedArray())

/**
 * Concatenates all non-null input values of each group from [this] string expression, separated by [separator].
 *
 * @param separator The separator to use between concatenated values. If left `null`, the database default will be used.
 * @param distinct If set to `true`, duplicate values will be eliminated.
 * @param orderBy If specified, values will be sorted in the concatenated string.
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.GroupByTests.testGroupConcat
 */
fun <T : String?> Expression<T>.groupConcat(
    separator: String? = null,
    distinct: Boolean = false,
    orderBy: Pair<Expression<*>, SortOrder>
): GroupConcat<T> = GroupConcat(this, separator, distinct, orderBy)

/**
 * Concatenates all non-null input values of each group from [this] string expression, separated by [separator].
 *
 * @param separator The separator to use between concatenated values. If left `null`, the database default will be used.
 * @param distinct If set to `true`, duplicate values will be eliminated.
 * @param orderBy If specified, values will be sorted in the concatenated string.
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.GroupByTests.testGroupConcat
 */
fun <T : String?> Expression<T>.groupConcat(
    separator: String? = null,
    distinct: Boolean = false,
    orderBy: Array<Pair<Expression<*>, SortOrder>> = emptyArray()
): GroupConcat<T> = GroupConcat(this, separator, distinct, orderBy = orderBy)

/** Extract a substring from this string expression that begins at the specified [start] and with the specified [length]. */
fun <T : String?> Expression<T>.substring(start: Int, length: Int): Substring<T> = Substring(this, intLiteral(start), intLiteral(length))

/** Removes the longest string containing only spaces from both ends of string expression. */
fun <T : String?> Expression<T>.trim(): Trim<T> = Trim(this)

/** Returns the index of the first occurrence of [substring] in this string expression or 0 if it doesn't contain [substring] */
fun <T : String?> Expression<T>.locate(substring: String): Locate<T> = Locate(this, substring)

// General-Purpose Aggregate Functions

/** Returns the minimum value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Any, S : T?> ExpressionWithColumnType<in S>.min(): Min<T, S> = Min<T, S>(this, this.columnType as IColumnType<T>)

/** Returns the maximum value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Any, S : T?> ExpressionWithColumnType<in S>.max(): Max<T, S> = Max<T, S>(this, this.columnType as IColumnType<T>)

/** Returns the average (arithmetic mean) value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<S>.avg(scale: Int = 2): Avg<T, S> = Avg<T, S>(this, scale)

/** Returns the sum of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T> ExpressionWithColumnType<T>.sum(): Sum<T> = Sum(this, this.columnType)

/** Returns the number of input rows for which the value of this expression is not null. */
fun ExpressionWithColumnType<*>.count(): Count = Count(this)

/** Returns the number of distinct input rows for which the value of this expression is not null. */
fun Column<*>.countDistinct(): Count = Count(this, true)

// Aggregate Functions for Statistics

/**
 * Returns the population standard deviation of the non-null input values, or `null` if there are no non-null values.
 *
 * @param scale The scale of the decimal column expression returned.
 */
fun <T : Any?> ExpressionWithColumnType<T>.stdDevPop(scale: Int = 2): StdDevPop<T> = StdDevPop(this, scale)

/**
 * Returns the sample standard deviation of the non-null input values, or `null` if there are no non-null values.
 *
 * @param scale The scale of the decimal column expression returned.
 */
fun <T : Any?> ExpressionWithColumnType<T>.stdDevSamp(scale: Int = 2): StdDevSamp<T> = StdDevSamp(this, scale)

/**
 * Returns the population variance of the non-null input values (square of the population standard deviation), or `null` if there are no non-null values.
 *
 * @param scale The scale of the decimal column expression returned.
 */
fun <T : Any?> ExpressionWithColumnType<T>.varPop(scale: Int = 2): VarPop<T> = VarPop(this, scale)

/**
 * Returns the sample variance of the non-null input values (square of the sample standard deviation), or `null` if there are no non-null values.
 *
 * @param scale The scale of the decimal column expression returned.
 */
fun <T : Any?> ExpressionWithColumnType<T>.varSamp(scale: Int = 2): VarSamp<T> = VarSamp(this, scale)

// Sequence Manipulation Functions

/** Advances this sequence and returns the new value. */
fun Sequence.nextIntVal(): NextVal<Int> = NextVal.IntNextVal(this)

/** Advances this sequence and returns the new value. */
fun Sequence.nextLongVal(): NextVal<Long> = NextVal.LongNextVal(this)

// Conditional Expressions

/**
 * Creates a conditional CASE expression builder where each WHEN clause contains
 * an independent boolean condition that is evaluated separately.
 *
 * This function creates a CASE expression without a comparison value, meaning each
 * WHEN clause will contain its own boolean condition. The conditions are evaluated
 * in order until the first one that evaluates to `true` is found, and its
 * corresponding result is returned.
 *
 * Example usage:
 * ```kotlin
 * case()
 *   .When(Users.age greater 18, "adult")
 *   .When(Users.age greater 13, "teenager")
 *   .Else("child")
 * ```
 *
 * @return A Case builder instance for creating conditional CASE expressions
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.ConditionsTests.nullOpInCaseTest
 */
fun case(): Case = Case()

/**
 * Creates a value-based CASE expression builder that compares a specific value
 * against different conditions in each WHEN clause.
 *
 * This function creates a CASE expression with a comparison value, where each WHEN
 * clause specifies a value or expression to compare against the provided [value].
 * The first matching condition determines the result that is returned.
 *
 * Example usage:
 * ```kotlin
 * case(Users.status)
 *   .When("ACTIVE", stringParam("User is active"))
 *   .When("INACTIVE", "User is inactive")
 *   .Else("Unknown status")
 * ```
 *
 * @param T The type of the value being compared
 * @param value The expression whose value will be compared against WHEN conditions
 * @return A ValueCase builder instance for creating value-based CASE expressions
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.ConditionsTests.nullOpInCaseTest
 */
fun <T> case(value: ExpressionWithColumnType<T>): ValueCase<T> = ValueCase(value)

/** Returns the first of its arguments that is not null. */
fun <T, S : T?> coalesce(
    expr: ExpressionWithColumnType<S>,
    alternate: Expression<out T>,
    vararg others: Expression<out T>
): Coalesce<T, S> = Coalesce<T, S>(expr, alternate, others = others)

// Value Expressions

/** Specifies a conversion from one data type to another. */
fun <R> Expression<*>.castTo(columnType: IColumnType<R & Any>): ExpressionWithColumnType<R> = Cast(this, columnType)

// Array Functions

/**
 * Returns the array element stored at the one-based [index] position, or `null` if the stored array itself is null.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.types.ArrayColumnTypeTests.testSelectUsingArrayGet
 */
infix operator fun <E, T : List<E>?> ExpressionWithColumnType<T>.get(index: Int): ArrayGet<E, T> {
    return when (this) {
        is ArrayGet<*, *> -> ArrayGet(this as Expression<T>, index, this.columnType as IColumnType<E & Any>) as ArrayGet<E, T>
        else -> ArrayGet(this, index, (this.columnType as ArrayColumnType<E, List<E>>).delegate)
    }
}

/**
 * Returns a subarray of elements stored from between [lower] and [upper] bounds (inclusive),
 * or `null` if the stored array itself is null.
 * **Note** If either bounds is left `null`, the database will use the stored array's respective lower or upper limit.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.types.ArrayColumnTypeTests.testSelectUsingArraySlice
 */
fun <E, T : List<E>?> ExpressionWithColumnType<T>.slice(lower: Int? = null, upper: Int? = null): ArraySlice<E, T> =
    ArraySlice(this, lower, upper, this.columnType)
