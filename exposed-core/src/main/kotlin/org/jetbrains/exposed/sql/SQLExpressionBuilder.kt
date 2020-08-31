package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal

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
): GroupConcat<T> = GroupConcat(this, separator, distinct, *orderBy)

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
fun CustomStringFunction(
    functionName: String,
    vararg params: Expression<*>
): CustomFunction<String?> = CustomFunction(functionName, VarCharColumnType(), *params)

/**
 * Calls a custom SQL function with the specified [functionName], that returns a long, and passing [params] as its arguments.
 */
fun CustomLongFunction(
    functionName: String,
    vararg params: Expression<*>
): CustomFunction<Long?> = CustomFunction(functionName, LongColumnType(), *params)

@Deprecated("Implement interface ISqlExpressionBuilder instead inherit this class")
open class SqlExpressionBuilderClass: ISqlExpressionBuilder

@Suppress("INAPPLICABLE_JVM_NAME")
interface ISqlExpressionBuilder {

    // Comparison Operators

    /** Checks if this expression is equals to some [t] value. */
    infix fun <T> ExpressionWithColumnType<T>.eq(t: T): Op<Boolean> = if (t == null) isNull() else EqOp(this, wrap(t))

    /** Checks if this expression is equals to some [t] value. */
    infix fun <T> CompositeColumn<T>.eq(t: T): Op<Boolean> {
        // For the composite column, create "EqOps" for each real column and combine it using "and" operator
        return this.getRealColumnsWithValues(t).entries
            .map { e -> (e.key as Column<Any?>).eq(e.value) }
            .compoundAnd()
    }

    /** Checks if this expression is equals to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): EqOp = EqOp(this, other)

    /** Checks if this expression is equals to some [t] value. */
    infix fun <T : Comparable<T>, E : EntityID<T>?> ExpressionWithColumnType<E>.eq(t: T?): Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        @Suppress("UNCHECKED_CAST")
        val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return EqOp(this, wrap(entityID))
    }


    /** Checks if this expression is not equals to some [other] value. */
    infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> = if (other == null) isNotNull() else NeqOp(this, wrap(other))

    /** Checks if this expression is not equals to some [other] expression. */
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): NeqOp = NeqOp(this, other)

    /** Checks if this expression is not equals to some [t] value. */
    infix fun <T : Comparable<T>> ExpressionWithColumnType<EntityID<T>>.neq(t: T?): Op<Boolean> = if (t == null) isNotNull() else NeqOp(this, wrap(t))


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


    // String Functions

    /** Concatenates the text representations of all the [expr]. */
    fun concat(vararg expr: Expression<*>): Concat = Concat("", *expr)

    /** Concatenates the text representations of all the [expr] using the specified [separator]. */
    fun concat(separator: String = "", expr: List<Expression<*>>): Concat = Concat(separator, *expr.toTypedArray())

    // Pattern Matching

    /** Checks if this expression matches the specified [pattern]. */
    infix fun <T : String?> Expression<T>.like(pattern: String): LikeOp = LikeOp(this, stringParam(pattern))

    /** Checks if this expression matches the specified [pattern]. */
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: String): LikeOp = LikeOp(this, stringParam(pattern))

    /** Checks if this expression matches the specified [pattern]. */
    infix fun <T : String?> Expression<T>.match(pattern: String): Op<Boolean> = match(pattern, null)

    /** Checks if this expression matches the specified [pattern] using the specified match [mode]. */
    fun <T : String?> Expression<T>.match(
        pattern: String,
        mode: FunctionProvider.MatchMode?
    ): Op<Boolean> = with(currentDialect.functionProvider) { this@match.match(pattern, mode) }

    /** Checks if this expression doesn't match the specified [pattern]. */
    infix fun <T : String?> Expression<T>.notLike(pattern: String): NotLikeOp = NotLikeOp(this, stringParam(pattern))

    /** Checks if this expression doesn't match the specified [pattern]. */
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: String): NotLikeOp = NotLikeOp(this, stringParam(pattern))

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    infix fun <T : String?> Expression<T>.regexp(pattern: String): RegexpOp<T> = RegexpOp(this, stringParam(pattern), true)

    /** Checks if this expression matches the [pattern]. Supports regular expressions. */
    fun <T : String?> Expression<T>.regexp(
        pattern: Expression<String>,
        caseSensitive: Boolean = true
    ): RegexpOp<T> = RegexpOp(this, pattern, caseSensitive)

    /** Checks if this expression doesn't match the [pattern]. Supports regular expressions. */
    @Deprecated("Use not(RegexpOp()) instead", ReplaceWith("regexp(pattern).not()"), DeprecationLevel.ERROR)
    infix fun <T : String?> ExpressionWithColumnType<T>.notRegexp(pattern: String): Op<Boolean> = TODO()


    // Conditional Expressions

    /** Returns the first of its arguments that is not null. */
    fun <T, S : T?, E : ExpressionWithColumnType<S>, R : T> coalesce(expr: E, alternate: ExpressionWithColumnType<out T>): Coalesce<T?, S, T?> =
        Coalesce(expr, alternate)

    fun case(value: Expression<*>? = null): Case = Case(value)


    // Subquery Expressions

    /** Checks if this expression is equals to any row returned from [query]. */
    infix fun <T> Expression<T>.inSubQuery(query: Query): InSubQueryOp<T> = InSubQueryOp(this, query)

    /** Checks if this expression is not equals to any row returned from [query]. */
    infix fun <T> Expression<T>.notInSubQuery(query: Query): NotInSubQueryOp<T> = NotInSubQueryOp(this, query)


    // Array Comparisons

    /** Checks if this expression is equals to any element from [list]. */
    infix fun <T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): InListOrNotInListOp<T> = InListOrNotInListOp(this, list, isInList = true)

    /** Checks if this expression is equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.inList(list: Iterable<T>): InListOrNotInListOp<EntityID<T>> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return inList(list.map { EntityIDFunctionProvider.createEntityID(it, idTable) })
    }

    /** Checks if this expression is not equals to any element from [list]. */
    infix fun <T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): InListOrNotInListOp<T> = InListOrNotInListOp(this, list, isInList = false)

    /** Checks if this expression is not equals to any element from [list]. */
    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListIds")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.notInList(list: Iterable<T>): InListOrNotInListOp<EntityID<T>> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return notInList(list.map { EntityIDFunctionProvider.createEntityID(it, idTable) })
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
object SqlExpressionBuilder: ISqlExpressionBuilder
