@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ops.InListOrNotInListBaseOp
import org.jetbrains.exposed.sql.ops.PairInListOp
import org.jetbrains.exposed.sql.ops.SingleValueInListOp
import org.jetbrains.exposed.sql.ops.TripleInListOp
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal
import kotlin.internal.LowPriorityInOverloadResolution

// String Functions

/** Converts this string expression to lower case. */
fun <T : String?> Expression<T>.lowerCase(): LowerCase<T> = LowerCase(this)

/** Converts this string expression to upper case. */
fun <T : String?> Expression<T>.upperCase(): UpperCase<T> = UpperCase(this)

fun <T : String?> Expression<T>.groupConcat(
    separator: String? = null,
    distinct: Boolean = false,
    orderBy: Pair<Expression<*>, SortOrder>
): GroupConcat<T> = GroupConcat(this, separator, distinct, orderBy)

fun <T : String?> Expression<T>.groupConcat(
    separator: String? = null,
    distinct: Boolean = false,
    orderBy: Array<Pair<Expression<*>, SortOrder>> = emptyArray()
): GroupConcat<T> = GroupConcat(this, separator, distinct, orderBy = orderBy)

/** Extract a substring from this string expression that begins at the specified [start] and with the specified [length]. */
fun <T : String?> Expression<T>.substring(start: Int, length: Int): Substring<T> = Substring(this, intLiteral(start), intLiteral(length))

/** Removes the longest string containing only spaces from both ends of string expression. */
fun <T : String?> Expression<T>.trim(): Trim<T> = Trim(this)

// General-Purpose Aggregate Functions

/** Returns the minimum value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.min(): Min<T, S> = Min<T, S>(this, this.columnType)

/** Returns the maximum value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.max(): Max<T, S> = Max<T, S>(this, this.columnType)

/** Returns the average (arithmetic mean) value of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.avg(scale: Int = 2): Avg<T, S> = Avg<T, S>(this, scale)

/** Returns the sum of this expression across all non-null input values, or `null` if there are no non-null values. */
fun <T : Any?> ExpressionWithColumnType<T>.sum(): Sum<T> = Sum(this, this.columnType)

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
@Deprecated("please use [nextIntVal] or [nextLongVal] functions", ReplaceWith("nextIntVal()"))
fun Sequence.nextVal(): NextVal<Int> = nextIntVal()

/** Advances this sequence and returns the new value. */
fun Sequence.nextIntVal(): NextVal<Int> = NextVal.IntNextVal(this)
/** Advances this sequence and returns the new value. */
fun Sequence.nextLongVal(): NextVal<Long> = NextVal.LongNextVal(this)

// Value Expressions

/** Specifies a conversion from one data type to another. */
fun <R> Expression<*>.castTo(columnType: IColumnType): ExpressionWithColumnType<R> = Cast(this, columnType)

// Misc.

/**
 * Calls a custom SQL function with the specified [functionName] and passes this expression as its only argument.
 */
fun <T : Any?> ExpressionWithColumnType<T>.function(functionName: String): CustomFunction<T?> = CustomFunction(functionName, columnType, this)

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

data class LikePattern(
    val pattern: String,
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

@Deprecated("Implement interface ISqlExpressionBuilder instead inherit this class")
open class SqlExpressionBuilderClass : ISqlExpressionBuilder

@Suppress("INAPPLICABLE_JVM_NAME", "TooManyFunctions")
interface ISqlExpressionBuilder {

    // Comparison Operators

    /** Checks if this expression is equals to some [t] value. */
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.eq(t: T): Op<Boolean> = if (t == null) isNull() else EqOp(this, wrap(t))

    /** Checks if this expression is equals to some [t] value. */
    infix fun <T> CompositeColumn<T>.eq(t: T): Op<Boolean> {
        // For the composite column, create "EqOps" for each real column and combine it using "and" operator
        return this.getRealColumnsWithValues(t).entries
            .map { e -> (e.key as Column<Any?>).eq(e.value) }
            .compoundAnd()
    }

    /** Checks if this expression is equals to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNull()
        else -> EqOp(this, other)
    }

    /** Checks if this expression is equals to some [t] value. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(t: V): Op<Boolean> {
        if (t == null) return isNull()

        @Suppress("UNCHECKED_CAST")
        val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return EqOp(this, wrap(entityID))
    }

    /** Checks if this expression is not equals to some [other] value. */
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> = if (other == null) isNotNull() else NeqOp(this, wrap(other))

    /** Checks if this expression is not equals to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): Op<Boolean> = when (other as Expression<*>) {
        is Op.NULL -> isNotNull()
        else -> NeqOp(this, other)
    }

    /** Checks if this expression is not equals to some [t] value. */
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(t: V): Op<Boolean> {
        if (t == null) return isNotNull()
        @Suppress("UNCHECKED_CAST")
        val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return NeqOp(this, wrap(entityID))
    }

    /** Checks if this expression is less than some [t] value. */
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.less(t: T): LessOp = LessOp(this, wrap(t))

    /** Checks if this expression is less than some [other] expression. */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.less(other: Expression<in S>): LessOp = LessOp(this, other)

    /** Checks if this expression is less than some [t] value. */
    @JvmName("lessEntityID")
    infix fun <T : Comparable<T>> ExpressionWithColumnType<EntityID<T>>.less(t: T): LessOp = LessOp(this, wrap(t))

    /** Checks if this expression is less than or equal to some [t] value */
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.lessEq(t: T): LessEqOp = LessEqOp(this, wrap(t))

    /** Checks if this expression is less than or equal to some [other] expression */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.lessEq(other: Expression<in S>): LessEqOp = LessEqOp(this, other)

    /** Checks if this expression is less than or equal to some [t] value */
    @JvmName("lessEqEntityID")
    infix fun <T : Comparable<T>> ExpressionWithColumnType<EntityID<T>>.lessEq(t: T): LessEqOp = LessEqOp(this, wrap(t))

    /** Checks if this expression is greater than some [t] value. */
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greater(t: T): GreaterOp = GreaterOp(this, wrap(t))

    /** Checks if this expression is greater than some [other] expression. */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greater(other: Expression<in S>): GreaterOp = GreaterOp(this, other)

    /** Checks if this expression is greater than some [t] value. */
    @JvmName("greaterEntityID")
    infix fun <T : Comparable<T>> ExpressionWithColumnType<EntityID<T>>.greater(t: T): GreaterOp = GreaterOp(this, wrap(t))

    /** Checks if this expression is greater than or equal to some [t] value */
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greaterEq(t: T): GreaterEqOp = GreaterEqOp(this, wrap(t))

    /** Checks if this expression is greater than or equal to some [other] expression */
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greaterEq(other: Expression<in S>): GreaterEqOp = GreaterEqOp(this, other)

    /** Checks if this expression is greater than or equal to some [t] value */
    @JvmName("greaterEqEntityID")
    infix fun <T : Comparable<T>> ExpressionWithColumnType<EntityID<T>>.greaterEq(t: T): GreaterEqOp = GreaterEqOp(this, wrap(t))

    // Comparison Predicates

    /** Returns `true` if this expression is between the values [from] and [to], `false` otherwise. */
    fun <T, S : T?> ExpressionWithColumnType<S>.between(from: T, to: T): Between = Between(this, wrap(from), wrap(to))

    /** Returns `true` if this expression is null, `false` otherwise. */
    fun <T> Expression<T>.isNull(): IsNullOp = IsNullOp(this)

    /** Returns `true` if this expression is not null, `false` otherwise. */
    fun <T> Expression<T>.isNotNull(): IsNotNullOp = IsNotNullOp(this)

    // Mathematical Operators

    /** Adds the [t] value to this expression. */
    infix operator fun <T> ExpressionWithColumnType<T>.plus(t: T): PlusOp<T, T> = PlusOp(this, wrap(t), columnType)

    /** Adds the [other] expression to this expression. */
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.plus(other: Expression<S>): PlusOp<T, S> = PlusOp(this, other, columnType)

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
    infix operator fun <T : Number?, S : T> ExpressionWithColumnType<T>.rem(t: S): ModOp<T, S> = ModOp(this, wrap(t), columnType)

    /** Calculates the remainder of dividing this expression by the [other] expression. */
    infix operator fun <T : Number?, S : Number> ExpressionWithColumnType<T>.rem(other: Expression<S>): ModOp<T, S> = ModOp(this, other, columnType)

    /** Calculates the remainder of dividing this expression by the [t] value. */
    infix fun <T : Number?, S : T> ExpressionWithColumnType<T>.mod(t: S): ModOp<T, S> = this % t

    /** Calculates the remainder of dividing this expression by the [other] expression. */
    infix fun <T : Number?, S : Number> ExpressionWithColumnType<T>.mod(other: Expression<S>): ModOp<T, S> = this % other

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
    infix fun <T : String?> Expression<T>.like(pattern: LikePattern): LikeEscapeOp = LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    /** Checks if this expression matches the specified [pattern]. */
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: String) = like(LikePattern(pattern))

    /** Checks if this expression matches the specified [pattern]. */
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: LikePattern): LikeEscapeOp = LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    /** Checks if this expression matches the specified [expression]. */
    infix fun <T : String?> Expression<T>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp = LikeEscapeOp(this, expression, true, null)

    /** Checks if this expression matches the specified [expression]. */
    @JvmName("likeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp = LikeEscapeOp(this, expression, true, null)

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
    infix fun <T : String?> Expression<T>.notLike(pattern: LikePattern): LikeEscapeOp = LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: String): LikeEscapeOp = notLike(LikePattern(pattern))

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: LikePattern): LikeEscapeOp = LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    /** Checks if this expression doesn't match the specified [pattern]. */
    infix fun <T : String?> Expression<T>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp = LikeEscapeOp(this, expression, false, null)

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp = LikeEscapeOp(this, expression, false, null)

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    infix fun <T : String?> Expression<T>.regexp(pattern: String): RegexpOp<T> = RegexpOp(this, stringParam(pattern), true)

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    fun <T : String?> Expression<T>.regexp(
        pattern: Expression<String>,
        caseSensitive: Boolean = true
    ): RegexpOp<T> = RegexpOp(this, pattern, caseSensitive)

    // Conditional Expressions

    /** Returns the first of its arguments that is not null. */
    fun <T, S : T?, A : Expression<out T>, R : T> coalesce(
        expr: ExpressionWithColumnType<S>,
        alternate: A,
        vararg others: A
    ): Coalesce<T?, S, R> = Coalesce(expr, alternate, others = others)

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

    /** Checks if this expression is equals to any element from [list]. */
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
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.inList(list: Iterable<Triple<T1, T2, T3>>): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = true)

    /** Checks if this expression is equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun <T : Comparable<T>, ID : EntityID<T>?> Column<ID>.inList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = true)
    }

    /** Checks if this expression is not equals to any element from [list]. */
    infix fun <T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<T> = SingleValueInListOp(this, list, isInList = false)

    /**
     * Checks if both expressions are not equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.notInList(list: Iterable<Pair<T1, T2>>): InListOrNotInListBaseOp<Pair<T1, T2>> =
        PairInListOp(this, list, isInList = false)

    /**
     * Checks if expressions from triple are not equal to elements from [list].
     * This syntax is unsupported by SQLite and SQL Server
     **/
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.notInList(list: Iterable<Triple<T1, T2, T3>>): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = false)

    /** Checks if this expression is not equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListIds")
    infix fun <T : Comparable<T>, ID : EntityID<T>?> Column<ID>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = false)
    }

    // Misc.

    /** Returns the specified [value] as a query parameter of type [T]. */
    @Suppress("UNCHECKED_CAST")
//    @ExperimentalUnsignedTypes
    fun <T, S : T?> ExpressionWithColumnType<in S>.wrap(value: T): QueryParameter<T> = when (value) {
        is Boolean -> booleanParam(value)
        is Byte -> byteParam(value)
        is UByte -> ubyteParam(value)
        is Short -> shortParam(value)
        is UShort -> ushortParam(value)
        is Int -> intParam(value)
        is UInt -> uintParam(value)
        is Long -> longParam(value)
        is ULong -> ulongParam(value)
        is Float -> floatParam(value)
        is Double -> doubleParam(value)
        is String -> QueryParameter(value, columnType) // String value should inherit from column
        else -> QueryParameter(value, columnType)
    } as QueryParameter<T>

    /** Returns the specified [value] as a literal of type [T]. */
    @Suppress("UNCHECKED_CAST")
//    @ExperimentalUnsignedTypes
    fun <T, S : T?> ExpressionWithColumnType<S>.asLiteral(value: T): LiteralOp<T> = when (value) {
        is Boolean -> booleanLiteral(value)
        is Byte -> byteLiteral(value)
        is UByte -> ubyteLiteral(value)
        is Short -> shortLiteral(value)
        is UShort -> ushortLiteral(value)
        is Int -> intLiteral(value)
        is UInt -> uintLiteral(value)
        is Long -> longLiteral(value)
        is ULong -> ulongLiteral(value)
        is Float -> floatLiteral(value)
        is Double -> doubleLiteral(value)
        is String -> stringLiteral(value)
        is ByteArray -> stringLiteral(value.toString(Charsets.UTF_8))
        else -> LiteralOp(columnType, value)
    } as LiteralOp<T>

    fun ExpressionWithColumnType<Int>.intToDecimal(): NoOpConversion<Int, BigDecimal> = NoOpConversion(this, DecimalColumnType(15, 0))
}

/**
 * Builder object for creating SQL expressions.
 */
object SqlExpressionBuilder : ISqlExpressionBuilder
