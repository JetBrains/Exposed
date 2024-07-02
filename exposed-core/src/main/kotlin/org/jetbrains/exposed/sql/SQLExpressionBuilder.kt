@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.functions.array.ArrayGet
import org.jetbrains.exposed.sql.functions.array.ArraySlice
import org.jetbrains.exposed.sql.ops.*
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal
import kotlin.internal.LowPriorityInOverloadResolution

// String Functions

/** Returns the length of this string expression, measured in characters, or `null` if this expression is null. */
fun <T : String?> Expression<T>.charLength(): CharLength<T> = CharLength(this)

/** Converts this string expression to lower case. */
fun <T : String?> Expression<T>.lowerCase(): LowerCase<T> = LowerCase(this)

/** Converts this string expression to upper case. */
fun <T : String?> Expression<T>.upperCase(): UpperCase<T> = UpperCase(this)

/**
 * Concatenates all non-null input values of each group from [this] string expression, separated by [separator].
 *
 * When [distinct] is set to `true`, duplicate values will be eliminated.
 * [orderBy] can be used to sort values in the concatenated string.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.GroupByTests.testGroupConcat
 */
fun <T : String?> Expression<T>.groupConcat(
    separator: String? = null,
    distinct: Boolean = false,
    orderBy: Pair<Expression<*>, SortOrder>
): GroupConcat<T> = GroupConcat(this, separator, distinct, orderBy)

/**
 * Concatenates all non-null input values of each group from [this] string expression, separated by [separator].
 *
 * When [distinct] is set to `true`, duplicate values will be eliminated.
 * [orderBy] can be used to sort values in the concatenated string by one or more expressions.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.GroupByTests.testGroupConcat
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
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.min(): Min<T, S> = Min<T, S>(this, this.columnType as IColumnType<T>)

/** Returns the maximum value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.max(): Max<T, S> = Max<T, S>(this, this.columnType as IColumnType<T>)

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

// Array Comparisons

/** Returns this subquery wrapped in the `ANY` operator. This function is not supported by the SQLite dialect. */
fun <T> anyFrom(subQuery: AbstractQuery<*>): Op<T> = AllAnyFromSubQueryOp(true, subQuery)

/**
 * Returns this array of data wrapped in the `ANY` operator. This function is only supported by PostgreSQL and H2 dialects.
 *
 * **Note** If [delegateType] is left `null`, the base column type associated with storing elements of type [T] will be
 * resolved according to the internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> anyFrom(array: Array<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyArray() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(true, array.toList(), columnType)
}

/**
 * Returns this list of data wrapped in the `ANY` operator. This function is only supported by PostgreSQL and H2 dialects.
 *
 * **Note** If [delegateType] is left `null`, the base column type associated with storing elements of type [T] will be
 * resolved according to the internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> anyFrom(array: List<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyList() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(true, array, columnType)
}

/** Returns this table wrapped in the `ANY` operator. This function is only supported by MySQL, PostgreSQL, and H2 dialects. */
fun <T> anyFrom(table: Table): Op<T> = AllAnyFromTableOp(true, table)

/** Returns this expression wrapped in the `ANY` operator. This function is only supported by PostgreSQL and H2 dialects. */
fun <E, T : List<E>?> anyFrom(expression: Expression<T>): Op<E> = AllAnyFromExpressionOp(true, expression)

/** Returns this subquery wrapped in the `ALL` operator. This function is not supported by the SQLite dialect. */
fun <T> allFrom(subQuery: AbstractQuery<*>): Op<T> = AllAnyFromSubQueryOp(false, subQuery)

/**
 * Returns this array of data wrapped in the `ALL` operator. This function is only supported by PostgreSQL and H2 dialects.
 *
 * **Note** If [delegateType] is left `null`, the base column type associated with storing elements of type [T] will be
 * resolved according to the internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> allFrom(array: Array<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyArray() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(false, array.toList(), columnType)
}

/**
 * Returns this list of data wrapped in the `ALL` operator. This function is only supported by PostgreSQL and H2 dialects.
 *
 * **Note** If [delegateType] is left `null`, the base column type associated with storing elements of type [T] will be
 * resolved according to the internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> allFrom(array: List<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyList() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(false, array, columnType)
}

/** Returns this table wrapped in the `ALL` operator. This function is only supported by MySQL, PostgreSQL, and H2 dialects. */
fun <T> allFrom(table: Table): Op<T> = AllAnyFromTableOp(false, table)

/** Returns this expression wrapped in the `ALL` operator. This function is only supported by PostgreSQL and H2 dialects. */
fun <E, T : List<E>?> allFrom(expression: Expression<T>): Op<E> = AllAnyFromExpressionOp(false, expression)

/**
 * Returns the array element stored at the one-based [index] position, or `null` if the stored array itself is null.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.types.ArrayColumnTypeTests.testSelectUsingArrayGet
 */
infix operator fun <E, T : List<E>?> ExpressionWithColumnType<T>.get(index: Int): ArrayGet<E, T> =
    ArrayGet(this, index, (this.columnType as ArrayColumnType<E>).delegate)

/**
 * Returns a subarray of elements stored from between [lower] and [upper] bounds (inclusive),
 * or `null` if the stored array itself is null.
 * **Note** If either bounds is left `null`, the database will use the stored array's respective lower or upper limit.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.types.ArrayColumnTypeTests.testSelectUsingArraySlice
 */
fun <E, T : List<E>?> ExpressionWithColumnType<T>.slice(lower: Int? = null, upper: Int? = null): ArraySlice<E, T> =
    ArraySlice(this, lower, upper, this.columnType)

// Sequence Manipulation Functions

/** Advances this sequence and returns the new value. */
fun Sequence.nextIntVal(): NextVal<Int> = NextVal.IntNextVal(this)

/** Advances this sequence and returns the new value. */
fun Sequence.nextLongVal(): NextVal<Long> = NextVal.LongNextVal(this)

// Value Expressions

/** Specifies a conversion from one data type to another. */
fun <R> Expression<*>.castTo(columnType: IColumnType<R & Any>): ExpressionWithColumnType<R> = Cast(this, columnType)

// Misc.

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

/** Represents all the operators available when building SQL expressions. */
@Suppress("INAPPLICABLE_JVM_NAME", "TooManyFunctions")
interface ISqlExpressionBuilder {

    // Comparison Operators
    // EQUAL

    /** Checks if this expression is equal to some [t] value. */
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.eq(t: T): Op<Boolean> = when {
        t == null -> isNull()
        columnType.isEntityIdentifier() -> {
            val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<*>
            table.mapIdComparison(t, ::EqOp)
        }
        else -> EqOp(this, wrap(t))
    }

    /** Checks if this expression is equal to some [t] value. */
    infix fun <T> CompositeColumn<T>.eq(t: T): Op<Boolean> {
        // For the composite column, create "EqOps" for each real column and combine it using "and" operator
        return this.getRealColumnsWithValues(t).entries
            .map { e -> (e.key as Column<Any?>).eq(e.value) }
            .compoundAnd()
    }

    /** Checks if this expression is equal to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNull()
        else -> EqOp(this, other)
    }

    /** Checks if this [EntityID] expression is equal to some [t] value. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(t: V): Op<Boolean> {
        if (t == null) return isNull()

        @Suppress("UNCHECKED_CAST")
        val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return if (table is CompositeIdTable) table.mapIdComparison(entityID, ::EqOp) else EqOp(this, wrap(entityID))
    }

    /** Checks if this [EntityID] expression is equal to some [other] expression. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(
        other: Expression<in V>
    ): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNull()
        else -> EqOp(this, other)
    }

    /** Checks if this expression is equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.eq(
        other: ExpressionWithColumnType<E>
    ): Op<Boolean> = other eq this

    // NOT EQUAL

    /** Checks if this expression is not equal to some [other] value. */
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> = when {
        other == null -> isNotNull()
        columnType.isEntityIdentifier() -> {
            val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<*>
            table.mapIdComparison(other, ::NeqOp)
        }
        else -> NeqOp(this, wrap(other))
    }

    /** Checks if this expression is not equal to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNotNull()
        else -> NeqOp(this, other)
    }

    /** Checks if this [EntityID] expression is not equal to some [t] value. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(t: V): Op<Boolean> {
        if (t == null) return isNotNull()
        @Suppress("UNCHECKED_CAST")
        val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return if (table is CompositeIdTable) table.mapIdComparison(entityID, ::NeqOp) else NeqOp(this, wrap(entityID))
    }

    /** Checks if this [EntityID] expression is not equal to some [other] expression. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(
        other: Expression<in V>
    ): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNotNull()
        else -> NeqOp(this, other)
    }

    /** Checks if this expression is not equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.neq(
        other: ExpressionWithColumnType<E>
    ): Op<Boolean> = other neq this

    // LESS THAN

    /** Checks if this expression is less than some [t] value. */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.less(t: T): LessOp = LessOp(this, wrap(t))

    /** Checks if this expression is less than some [other] expression. */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.less(other: Expression<in S>): LessOp = LessOp(this, other)

    /** Checks if this [EntityID] expression is less than some [t] value. */
    @JvmName("lessEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.less(t: T): LessOp =
        LessOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is less than some [other] expression. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.less(
        other: Expression<in V>
    ): LessOp = LessOp(this, other)

    /** Checks if this expression is less than some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.less(
        other: ExpressionWithColumnType<E>
    ): LessOp = LessOp(this, other)

    // LESS THAN OR EQUAL

    /** Checks if this expression is less than or equal to some [t] value */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.lessEq(t: T): LessEqOp = LessEqOp(this, wrap(t))

    /** Checks if this expression is less than or equal to some [other] expression */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.lessEq(other: Expression<in S>): LessEqOp = LessEqOp(this, other)

    /** Checks if this [EntityID] expression is less than or equal to some [t] value */
    @JvmName("lessEqEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.lessEq(t: T): LessEqOp =
        LessEqOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is less than or equal to some [other] expression */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.lessEq(
        other: Expression<in V>
    ): LessEqOp = LessEqOp(this, other)

    /** Checks if this expression is less than or equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.lessEq(
        other: ExpressionWithColumnType<E>
    ): LessEqOp = LessEqOp(this, other)

    // GREATER THAN

    /** Checks if this expression is greater than some [t] value. */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greater(t: T): GreaterOp = GreaterOp(this, wrap(t))

    /** Checks if this expression is greater than some [other] expression. */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greater(other: Expression<in S>): GreaterOp = GreaterOp(this, other)

    /** Checks if this [EntityID] expression is greater than some [t] value. */
    @JvmName("greaterEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.greater(t: T): GreaterOp =
        GreaterOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is greater than some [other] expression. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.greater(
        other: Expression<in V>
    ): GreaterOp = GreaterOp(this, other)

    /** Checks if this expression is greater than some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.greater(
        other: ExpressionWithColumnType<E>
    ): GreaterOp = GreaterOp(this, other)

    // GREATER THAN OR EQUAL

    /** Checks if this expression is greater than or equal to some [t] value */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greaterEq(t: T): GreaterEqOp = GreaterEqOp(this, wrap(t))

    /** Checks if this expression is greater than or equal to some [other] expression */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greaterEq(other: Expression<in S>): GreaterEqOp = GreaterEqOp(this, other)

    /** Checks if this [EntityID] expression is greater than or equal to some [t] value */
    @JvmName("greaterEqEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.greaterEq(t: T): GreaterEqOp =
        GreaterEqOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is greater than or equal to some [other] expression */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.greaterEq(
        other: Expression<in V>
    ): GreaterEqOp = GreaterEqOp(this, other)

    /** Checks if this expression is greater than or equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.greaterEq(
        other: ExpressionWithColumnType<E>
    ): GreaterEqOp = GreaterEqOp(this, other)

    // Comparison Predicates

    /** Returns `true` if this expression is between the values [from] and [to], `false` otherwise. */
    fun <T, S : T?> ExpressionWithColumnType<in S>.between(from: T, to: T): Between = Between(this, wrap(from), wrap(to))

    /** Returns `true` if this [EntityID] expression is between the values [from] and [to], `false` otherwise. */
    fun <T : Comparable<T>, E : EntityID<T>?> Column<E>.between(from: T, to: T): Between =
        Between(this, wrap(EntityID(from, this.idTable())), wrap(EntityID(to, this.idTable())))

    /** Returns `true` if this expression is null, `false` otherwise. */
    fun <T> Expression<T>.isNull() = if (this is Column<*> && columnType.isEntityIdentifier()) {
        (table as IdTable<*>).mapIdOperator(::IsNullOp)
    } else {
        IsNullOp(this)
    }

    /** Returns `true` if this expression is not null, `false` otherwise. */
    fun <T> Expression<T>.isNotNull() = if (this is Column<*> && columnType.isEntityIdentifier()) {
        (table as IdTable<*>).mapIdOperator(::IsNotNullOp)
    } else {
        IsNotNullOp(this)
    }

    /** Checks if this expression is equal to some [t] value, with `null` treated as a comparable value */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isNotDistinctFrom(t: T): IsNotDistinctFromOp = IsNotDistinctFromOp(this, wrap(t))

    /** Checks if this expression is equal to some [other] expression, with `null` treated as a comparable value */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.isNotDistinctFrom(other: Expression<in S>): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    /** Checks if this expression is equal to some [t] value, with `null` treated as a comparable value */
    @JvmName("isNotDistinctFromEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.isNotDistinctFrom(t: T): IsNotDistinctFromOp =
        IsNotDistinctFromOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is equal to some [other] expression */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isNotDistinctFrom(
        other: Expression<in V>
    ): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    /** Checks if this expression is equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.isNotDistinctFrom(
        other: ExpressionWithColumnType<E>
    ): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    /** Checks if this expression is not equal to some [t] value, with `null` treated as a comparable value */
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isDistinctFrom(t: T): IsDistinctFromOp = IsDistinctFromOp(this, wrap(t))

    /** Checks if this expression is not equal to some [other] expression, with `null` treated as a comparable value */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.isDistinctFrom(other: Expression<in S>): IsDistinctFromOp = IsDistinctFromOp(this, other)

    /** Checks if this expression is not equal to some [t] value, with `null` treated as a comparable value */
    @JvmName("isDistinctFromEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.isDistinctFrom(t: T): IsDistinctFromOp =
        IsDistinctFromOp(this, wrap(EntityID(t, this.idTable())))

    /** Checks if this [EntityID] expression is not equal to some [other] expression */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isDistinctFrom(
        other: Expression<in V>
    ): IsDistinctFromOp = IsDistinctFromOp(this, other)

    /** Checks if this expression is not equal to some [other] [EntityID] expression. */
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<in V>.isDistinctFrom(
        other: ExpressionWithColumnType<E>
    ): IsDistinctFromOp = IsDistinctFromOp(this, other)

    // Mathematical Operators

    /** Adds the [t] value to this expression. */
    infix operator fun <T> ExpressionWithColumnType<T>.plus(t: T): PlusOp<T, T> = PlusOp(this, wrap(t), columnType)

    /** Adds the [other] expression to this expression. */
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.plus(other: Expression<S>): PlusOp<T, S> = PlusOp(this, other, columnType)

    /**
     * Concatenate the value to the input expression.
     *
     * @param value The string value to be concatenated.
     * @return The concatenated expression.
     */
    infix operator fun Expression<String>.plus(value: String): Concat = concat(this, stringLiteral(value))

    /**
     * Concatenate the value to the input expression.
     *
     * @param value The string value to be concatenated.
     * @return The concatenated expression.
     */
    infix operator fun Expression<String>.plus(value: Expression<String>): Concat = concat(this, value)

    /**
     * Concatenate the value to the input expression.
     *
     * @param value The string value to be concatenated.
     * @return The concatenated expression.
     */
    infix operator fun String.plus(value: Expression<String>): Concat = concat(stringLiteral(this), value)

    /** Subtracts the [t] value from this expression. */
    infix operator fun <T> ExpressionWithColumnType<T>.minus(t: T): MinusOp<T, T> = MinusOp(this, wrap(t), columnType)

    /** Subtracts the [other] expression from this expression. */
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.minus(other: Expression<S>): MinusOp<T, S> = MinusOp(this, other, columnType)

    /** Multiplies this expression by the [t] value. */
    infix operator fun <T> ExpressionWithColumnType<T>.times(t: T): TimesOp<T, T> = TimesOp(this, wrap(t), columnType)

    /** Multiplies this expression by the [other] expression. */
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.times(other: Expression<S>): TimesOp<T, S> = TimesOp(this, other, columnType)

    /** Divides this expression by the [t] value. */
    infix operator fun <T> ExpressionWithColumnType<T>.div(t: T): DivideOp<T, T> = DivideOp(this, wrap(t), columnType)

    /** Divides this expression by the [other] expression. */
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.div(other: Expression<S>): DivideOp<T, S> = DivideOp(this, other, columnType)

    /** Calculates the remainder of dividing this expression by the [t] value. */
    infix operator fun <T : Number?, S : T> ExpressionWithColumnType<T>.rem(t: S) = ModOp<T, S, T>(this, wrap(t), columnType)

    /** Calculates the remainder of dividing this expression by the [other] expression. */
    infix operator fun <T : Number?, S : Number> ExpressionWithColumnType<T>.rem(other: Expression<S>) = ModOp<T, S, T>(this, other, columnType)

    /** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number. */
    @JvmName("remWithEntityId")
    infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(other: S) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    /** Calculates the remainder of dividing [this] number expression by [other] numeric PK */
    @JvmName("remWithEntityId2")
    infix operator fun <T, S : Number, ID : EntityID<T>?> Expression<S>.rem(other: ExpressionWithColumnType<ID>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    /** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number expression. */
    @JvmName("remWithEntityId3")
    infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(other: Expression<S>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    /** Calculates the remainder of dividing this expression by the [t] value. */
    infix fun <T : Number?, S : T> ExpressionWithColumnType<T>.mod(t: S) = this % t

    /** Calculates the remainder of dividing this expression by the [other] expression. */
    infix fun <T : Number?, S : Number> ExpressionWithColumnType<T>.mod(other: Expression<S>) = this % other

    /** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number. */
    @JvmName("modWithEntityId")
    infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(other: S) where T : Number, T : Comparable<T> = this % other

    /** Calculates the remainder of dividing [this] number expression by [other] numeric PK */
    @JvmName("modWithEntityId2")
    infix fun <T, S : Number, ID : EntityID<T>?> Expression<S>.mod(other: ExpressionWithColumnType<ID>) where T : Number, T : Comparable<T> = this % other

    /** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number expression. */
    @JvmName("modWithEntityId3")
    infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(other: Expression<S>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    /**
     * Performs a bitwise `and` on this expression and [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseAnd(t: T): AndBitOp<T, T> = AndBitOp(this, wrap(t), columnType)

    /**
     * Performs a bitwise `and` on this expression and expression [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseAnd(t: Expression<T>): AndBitOp<T, T> = AndBitOp(this, t, columnType)

    /**
     * Performs a bitwise `or` on this expression and [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseOr(t: T): OrBitOp<T, T> = OrBitOp(this, wrap(t), columnType)

    /**
     * Performs a bitwise `or` on this expression and expression [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseOr(t: Expression<T>): OrBitOp<T, T> = OrBitOp(this, t, columnType)

    /**
     * Performs a bitwise `or` on this expression and [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseXor(t: T): XorBitOp<T, T> = XorBitOp(this, wrap(t), columnType)

    /**
     * Performs a bitwise `or` on this expression and expression [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.bitwiseXor(t: Expression<T>): XorBitOp<T, T> = XorBitOp(this, t, columnType)

    /**
     * Performs a bitwise `and` on this expression and [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.hasFlag(t: T): EqOp = EqOp(AndBitOp(this, wrap(t), columnType), wrap(t))

    /**
     * Performs a bitwise `and` on this expression and expression [t].
     */
    infix fun <T> ExpressionWithColumnType<T>.hasFlag(t: Expression<T>): EqOp = EqOp(AndBitOp(this, t, columnType), wrap(t))

    // String Functions

    /** Concatenates the text representations of all the [expr]. */
    fun concat(vararg expr: Expression<*>): Concat = Concat("", *expr)

    /** Concatenates the text representations of all the [expr] using the specified [separator]. */
    fun concat(separator: String = "", expr: List<Expression<*>>): Concat = Concat(separator, expr = expr.toTypedArray())

    // Pattern Matching

    /** Checks if this expression matches the specified [pattern]. */
    infix fun <T : String?> Expression<T>.like(pattern: String) = like(LikePattern(pattern))

    /** Checks if this expression matches the specified [pattern]. */
    infix fun <T : String?> Expression<T>.like(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    /** Checks if this expression matches the specified [pattern]. */
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: String) = like(LikePattern(pattern))

    /** Checks if this expression matches the specified [pattern]. */
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    /** Checks if this expression matches the specified [expression]. */
    infix fun <T : String?> Expression<T>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, true, null)

    /** Checks if this expression matches the specified [expression]. */
    @JvmName("likeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, true, null)

    /** Checks if this expression matches the specified [pattern]. */
    infix fun <T : String?> Expression<T>.match(pattern: String): Op<Boolean> = match(pattern, null)

    /** Checks if this expression matches the specified [pattern] using the specified match [mode]. */
    fun <T : String?> Expression<T>.match(
        pattern: String,
        mode: FunctionProvider.MatchMode?
    ): Op<Boolean> = with(currentDialect.functionProvider) { this@match.match(pattern, mode) }

    /** Checks if this expression doesn't match the specified [pattern]. */
    infix fun <T : String?> Expression<T>.notLike(pattern: String): LikeEscapeOp = notLike(LikePattern(pattern))

    /** Checks if this expression doesn't match the specified [pattern]. */
    infix fun <T : String?> Expression<T>.notLike(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: String): LikeEscapeOp = notLike(LikePattern(pattern))

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    /** Checks if this expression doesn't match the specified [pattern]. */
    infix fun <T : String?> Expression<T>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, false, null)

    /** Checks if this expression doesn't match the specified [expression]. */
    @JvmName("notLikeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, false, null)

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    infix fun <T : String?> Expression<T>.regexp(pattern: String): RegexpOp<T> = RegexpOp(this, stringParam(pattern), true)

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    fun <T : String?> Expression<T>.regexp(
        pattern: Expression<String>,
        caseSensitive: Boolean = true
    ): RegexpOp<T> = RegexpOp(this, pattern, caseSensitive)

    // Window Functions

    /** Returns the number of the current row within its partition, counting from 1. */
    fun rowNumber(): RowNumber = RowNumber()

    /** Returns the rank of the current row, with gaps; that is, the row_number of the first row in its peer group. */
    fun rank(): Rank = Rank()

    /** Returns the rank of the current row, without gaps; this function effectively counts peer groups. */
    fun denseRank(): DenseRank = DenseRank()

    /**
     * Returns the relative rank of the current row, that is (rank - 1) / (total partition rows - 1).
     * The value thus ranges from 0 to 1 inclusive.
     */
    fun percentRank(): PercentRank = PercentRank()

    /**
     * Returns the cumulative distribution, that is (number of partition rows preceding or peers with current row) /
     * (total partition rows). The value thus ranges from 1/N to 1.
     */
    fun cumeDist(): CumeDist = CumeDist()

    /** Returns an integer ranging from 1 to the [numBuckets], dividing the partition as equally as possible. */
    fun ntile(numBuckets: ExpressionWithColumnType<Int>): Ntile = Ntile(numBuckets)

    /**
     * Returns value evaluated at the row that is [offset] rows before the current row within the partition;
     * if there is no such row, instead returns [defaultValue].
     * Both [offset] and [defaultValue] are evaluated with respect to the current row.
     */
    fun <T> ExpressionWithColumnType<T>.lag(
        offset: ExpressionWithColumnType<Int> = intLiteral(1),
        defaultValue: ExpressionWithColumnType<T>? = null
    ): Lag<T> = Lag(this, offset, defaultValue)

    /**
     * Returns value evaluated at the row that is [offset] rows after the current row within the partition;
     * if there is no such row, instead returns [defaultValue].
     * Both [offset] and [defaultValue] are evaluated with respect to the current row.
     */
    fun <T> ExpressionWithColumnType<T>.lead(
        offset: ExpressionWithColumnType<Int> = intLiteral(1),
        defaultValue: ExpressionWithColumnType<T>? = null
    ): Lead<T> = Lead(this, offset, defaultValue)

    /**
     * Returns value evaluated at the row that is the first row of the window frame.
     */
    fun <T> ExpressionWithColumnType<T>.firstValue(): FirstValue<T> = FirstValue(this)

    /**
     * Returns value evaluated at the row that is the last row of the window frame.
     */
    fun <T> ExpressionWithColumnType<T>.lastValue(): LastValue<T> = LastValue(this)

    /**
     * Returns value evaluated at the row that is the [n]'th row of the window frame
     * (counting from 1); null if no such row.
     */
    fun <T> ExpressionWithColumnType<T>.nthValue(n: ExpressionWithColumnType<Int>): NthValue<T> = NthValue(this, n)

    // Conditional Expressions

    /** Returns the first of its arguments that is not null. */
    fun <T, S : T?> coalesce(
        expr: ExpressionWithColumnType<S>,
        alternate: Expression<out T>,
        vararg others: Expression<out T>
    ): Coalesce<T, S> = Coalesce<T, S>(expr, alternate, others = others)

    /**
     * Compares [value] against any chained conditional expressions.
     *
     * If [value] is `null`, chained conditionals will be evaluated separately until the first is evaluated as `true`.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.ConditionsTests.nullOpInCaseTest
     */
    fun case(value: Expression<*>? = null): Case = Case(value)

    // Subquery Expressions

    /** Checks if this expression is equals to any row returned from [query]. */
    infix fun <T> Expression<T>.inSubQuery(query: AbstractQuery<*>): InSubQueryOp<T> = InSubQueryOp(this, query)

    /** Checks if this expression is not equals to any row returned from [query]. */
    infix fun <T> Expression<T>.notInSubQuery(query: AbstractQuery<*>): NotInSubQueryOp<T> = NotInSubQueryOp(this, query)

    /** Checks if this expression is equals to single value returned from [query]. */
    infix fun <T> Expression<T>.eqSubQuery(query: AbstractQuery<*>): EqSubQueryOp<T> = EqSubQueryOp(this, query)

    /** Checks if this expression is not equals to single value returned from [query]. */
    infix fun <T> Expression<T>.notEqSubQuery(query: AbstractQuery<*>): NotEqSubQueryOp<T> = NotEqSubQueryOp(this, query)

    // Array Comparisons

    /** Checks if this expression is equal to any element from [list]. */
    infix fun <T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): InListOrNotInListBaseOp<T> = SingleValueInListOp(this, list, isInList = true)

    /**
     * Checks if both expressions are equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.inList(list: Iterable<Pair<T1, T2>>): InListOrNotInListBaseOp<Pair<T1, T2>> =
        PairInListOp(this, list, isInList = true)

    /**
     * Checks if expressions from triple are equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.inList(
        list: Iterable<Triple<T1, T2, T3>>
    ): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = true)

    /** Checks if this expression is equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun <T : Comparable<T>, ID : EntityID<T>?> Column<ID>.inList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = true)
    }

    /** Checks if this expression is not equals to any element from [list]. */
    infix fun <T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<T> =
        SingleValueInListOp(this, list, isInList = false)

    /**
     * Checks if both expressions are not equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.notInList(
        list: Iterable<Pair<T1, T2>>
    ): InListOrNotInListBaseOp<Pair<T1, T2>> =
        PairInListOp(this, list, isInList = false)

    /**
     * Checks if expressions from triple are not equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.notInList(
        list: Iterable<Triple<T1, T2, T3>>
    ): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = false)

    /** Checks if this expression is not equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListIds")
    infix fun <T : Comparable<T>, ID : EntityID<T>?> Column<ID>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = false)
    }

    // "IN (TABLE ...)" comparisons

    /**
     * Checks if this expression is equal to any element from the column of [table] with only a single column.
     *
     * **Note** This function is only supported by MySQL, PostgreSQL, and H2 dialects.
     */
    infix fun <T> ExpressionWithColumnType<T>.inTable(table: Table): InTableOp = InTableOp(this, table, true)

    /**
     * Checks if this expression is **not** equal to any element from the column of [table] with only a single column.
     *
     * **Note** This function is only supported by MySQL, PostgreSQL, and H2 dialects.
     */
    infix fun <T> ExpressionWithColumnType<T>.notInTable(table: Table): InTableOp = InTableOp(this, table, false)

    // Misc.

    /** Returns the specified [value] as a query parameter of type [T]. */
    @Suppress("UNCHECKED_CAST")
    fun <T, S : T?> ExpressionWithColumnType<in S>.wrap(value: T): QueryParameter<T> =
        QueryParameter(value, columnType as IColumnType<T & Any>)

    /** Returns the specified [value] as a literal of type [T]. */
    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    fun <T, S : T?> ExpressionWithColumnType<S>.asLiteral(value: T): LiteralOp<T> = when {
        value is ByteArray && columnType is BasicBinaryColumnType -> stringLiteral(value.toString(Charsets.UTF_8))
        else -> LiteralOp(columnType as IColumnType<T & Any>, value)
    } as LiteralOp<T>

    fun ExpressionWithColumnType<Int>.intToDecimal(): NoOpConversion<Int, BigDecimal> =
        NoOpConversion(this, DecimalColumnType(precision = 15, scale = 0))

    private fun <T : Comparable<T>, E : EntityID<T>> Column<out E?>.idTable(): IdTable<T> =
        when (val table = this.foreignKey?.targetTable ?: this.table) {
            is Alias<*> -> table.delegate
            else -> table
        } as IdTable<T>
}

/**
 * Builder object for creating SQL expressions.
 */
object SqlExpressionBuilder : ISqlExpressionBuilder
