@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "TooManyFunctions")

package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.ops.*
import org.jetbrains.exposed.v1.core.vendors.FunctionProvider
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import java.math.BigDecimal
import kotlin.internal.LowPriorityInOverloadResolution

// Logical Operators

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
inline fun Expression<Boolean>.and(op: () -> Op<Boolean>): Op<Boolean> = and(op())

/**  Returns the result of performing a logical `or` operation between this expression and the [op].*/
inline fun Expression<Boolean>.or(op: () -> Op<Boolean>): Op<Boolean> = or(op())

/** Returns the result of performing a logical `and` operation between this expression and the negate [op]. */
inline fun Expression<Boolean>.andNot(op: () -> Op<Boolean>): Op<Boolean> = and(not(op()))

/** Returns the result of performing a logical `or` operation between this expression and the negate [op]. */
inline fun Expression<Boolean>.orNot(op: () -> Op<Boolean>): Op<Boolean> = or(not(op()))

/**
 * Returns the result of performing a logical `and` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
inline fun Op<Boolean>.andIfNotNull(op: () -> Op<Boolean>?): Op<Boolean> = andIfNotNull(op())

/**
 * Returns the result of performing a logical `or` operation between this expression and the [op] **if** [op] is not null.
 * Otherwise, this expression will be returned.
 */
inline fun Op<Boolean>.orIfNotNull(op: () -> Op<Boolean>?): Op<Boolean> = orIfNotNull(op())

// Comparison Operators

// EQUAL

/** Checks if this expression is equal to some [t] value. */
@LowPriorityInOverloadResolution
infix fun <T> ExpressionWithColumnType<T>.eq(t: T): Op<Boolean> = when {
    t == null -> isNull()
    (this as? Column<*>)?.isEntityIdentifier() == true -> table.mapIdComparison(t, ::EqOp)
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
infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): Op<Boolean> = when {
    (other as Expression<*>) is Op.NULL -> isNull()
    (other as? QueryParameter)?.compositeValue != null -> (this as Column<*>).table.mapIdComparison(other.value, ::EqOp)
    else -> EqOp(this, other)
}

/** Checks if this [EntityID] expression is equal to some [t] value. */
@JvmName("eqEntityIDValue")
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(t: V): Op<Boolean> {
    if (t == null) return isNull()

    @Suppress("UNCHECKED_CAST")
    val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
    val entityID = EntityID(t, table)
    return if ((this as? Column<*>)?.isEntityIdentifier() == true) {
        this.table.mapIdComparison(entityID, ::EqOp)
    } else {
        EqOp(this, wrap(entityID))
    }
}

/** Checks if this [EntityID] expression is equal to some [other] expression. */
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(
    other: Expression<V>
): Op<Boolean> = when (other as Expression<*>) {
    is Op.NULL -> isNull()
    else -> EqOp(this, other)
}

/** Checks if this expression is equal to some [other] [EntityID] expression. */
infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.eq(
    other: ExpressionWithColumnType<E>
): Op<Boolean> = other eq this

// NOT EQUAL

/** Checks if this expression is not equal to some [other] value. */
@LowPriorityInOverloadResolution
infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> = when {
    other == null -> isNotNull()
    (this as? Column<*>)?.isEntityIdentifier() == true -> table.mapIdComparison(other, ::NeqOp)
    else -> NeqOp(this, wrap(other))
}

/** Checks if this expression is not equal to some [other] expression. */
infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): Op<Boolean> = when {
    (other as Expression<*>) is Op.NULL -> isNotNull()
    (other as? QueryParameter)?.compositeValue != null -> (this as Column<*>).table.mapIdComparison(other.value, ::NeqOp)
    else -> NeqOp(this, other)
}

/** Checks if this [EntityID] expression is not equal to some [t] value. */
@JvmName("neqEntityIDValue")
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(t: V): Op<Boolean> {
    if (t == null) return isNotNull()
    @Suppress("UNCHECKED_CAST")
    val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
    val entityID = EntityID(t, table)
    return if ((this as? Column<*>)?.isEntityIdentifier() == true) {
        this.table.mapIdComparison(entityID, ::NeqOp)
    } else {
        NeqOp(this, wrap(entityID))
    }
}

/** Checks if this [EntityID] expression is not equal to some [other] expression. */
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(
    other: Expression<V>
): Op<Boolean> = when (other as Expression<*>) {
    is Op.NULL -> isNotNull()
    else -> NeqOp(this, other)
}

/** Checks if this expression is not equal to some [other] [EntityID] expression. */
infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.neq(
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

/** Checks if this [EntityID] expression is less than some [other] [EntityID] expression. */
@JvmName("lessBetweenEntityIDs")
infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.less(
    other: Expression<E>
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

/** Checks if this [EntityID] expression is less than or equal to some [other] [EntityID] expression. */
@JvmName("lessEqBetweenEntityIDs")
infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.lessEq(
    other: Expression<E>
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

/** Checks if this [EntityID] expression is greater than some [other] [EntityID] expression. */
@JvmName("greaterBetweenEntityIDs")
infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.greater(
    other: Expression<E>
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

/** Checks if this [EntityID] expression is greater than or equal to some [other] [EntityID] expression. */
@JvmName("greaterEqBetweenEntityIDs")
infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.greaterEq(
    other: Expression<E>
): GreaterEqOp = GreaterEqOp(this, other)

// Miscellaneous Comparisons

/** Returns `true` if this expression is between the values [from] and [to], `false` otherwise. */
fun <T, S : T?> ExpressionWithColumnType<in S>.between(from: T, to: T): Between = Between(this, wrap(from), wrap(to))

/** Returns `true` if this [EntityID] expression is between the values [from] and [to], `false` otherwise. */
fun <T : Any, E : EntityID<T>?> Column<E>.between(from: T, to: T): Between =
    Between(this, wrap(EntityID(from, this.idTable())), wrap(EntityID(to, this.idTable())))

/** Returns `true` if this expression is null, `false` otherwise. */
fun <T> Expression<T>.isNull(): Op<Boolean> = when {
    this is Column<*> && isEntityIdentifier() -> table.mapIdOperator(::IsNullOp)
    this is QueryParameter && compositeValue != null -> {
        val table = compositeValue.values.keys.first().table
        table.mapIdOperator(::IsNullOp)
    }
    else -> IsNullOp(this)
}

/** Returns `true` if this string expression is null or empty, `false` otherwise. */
fun <T : String?> Expression<T>.isNullOrEmpty(): Op<Boolean> = IsNullOp(this).or { this@isNullOrEmpty.charLength() eq 0 }

/** Returns `true` if this expression is not null, `false` otherwise. */
fun <T> Expression<T>.isNotNull(): Op<Boolean> = when {
    this is Column<*> && isEntityIdentifier() -> table.mapIdOperator(::IsNotNullOp)
    this is QueryParameter && compositeValue != null -> {
        val table = compositeValue.values.keys.first().table
        table.mapIdOperator(::IsNotNullOp)
    }
    else -> IsNotNullOp(this)
}

/** Checks if this expression is equal to some [t] value, with `null` treated as a comparable value */
@LowPriorityInOverloadResolution
infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isNotDistinctFrom(t: T): IsNotDistinctFromOp = IsNotDistinctFromOp(this, wrap(t))

/** Checks if this expression is equal to some [other] expression, with `null` treated as a comparable value */
infix fun <T : Comparable<T>, S : T?> Expression<in S>.isNotDistinctFrom(other: Expression<in S>): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

/** Checks if this expression is equal to some [t] value, with `null` treated as a comparable value */
@JvmName("isNotDistinctFromEntityID")
infix fun <T : Any> Column<EntityID<T>>.isNotDistinctFrom(t: T): IsNotDistinctFromOp =
    IsNotDistinctFromOp(this, wrap(EntityID(t, this.idTable())))

/** Checks if this [EntityID] expression is equal to some [other] expression */
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isNotDistinctFrom(
    other: Expression<in V>
): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

/** Checks if this expression is equal to some [other] [EntityID] expression. */
infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.isNotDistinctFrom(
    other: ExpressionWithColumnType<E>
): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

/** Checks if this expression is not equal to some [t] value, with `null` treated as a comparable value */
@LowPriorityInOverloadResolution
infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isDistinctFrom(t: T): IsDistinctFromOp = IsDistinctFromOp(this, wrap(t))

/** Checks if this expression is not equal to some [other] expression, with `null` treated as a comparable value */
infix fun <T : Comparable<T>, S : T?> Expression<in S>.isDistinctFrom(other: Expression<in S>): IsDistinctFromOp = IsDistinctFromOp(this, other)

/** Checks if this expression is not equal to some [t] value, with `null` treated as a comparable value */
@JvmName("isDistinctFromEntityID")
infix fun <T : Any> Column<EntityID<T>>.isDistinctFrom(t: T): IsDistinctFromOp =
    IsDistinctFromOp(this, wrap(EntityID(t, this.idTable())))

/** Checks if this [EntityID] expression is not equal to some [other] expression */
infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isDistinctFrom(
    other: Expression<in V>
): IsDistinctFromOp = IsDistinctFromOp(this, other)

/** Checks if this expression is not equal to some [other] [EntityID] expression. */
infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<in V>.isDistinctFrom(
    other: ExpressionWithColumnType<E>
): IsDistinctFromOp = IsDistinctFromOp(this, other)

private fun <T : Any, E : EntityID<T>> Column<out E?>.idTable(): IdTable<T> =
    when (val table = this.foreignKey?.targetTable ?: this.table) {
        is Alias<*> -> table.delegate
        else -> table
    } as IdTable<T>

// Mathematical Operators

// PLUS

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

// MINUS

/** Subtracts the [t] value from this expression. */
infix operator fun <T> ExpressionWithColumnType<T>.minus(t: T): MinusOp<T, T> = MinusOp(this, wrap(t), columnType)

/** Subtracts the [other] expression from this expression. */
infix operator fun <T, S : T> ExpressionWithColumnType<T>.minus(other: Expression<S>): MinusOp<T, S> = MinusOp(this, other, columnType)

// TIMES

/** Multiplies this expression by the [t] value. */
infix operator fun <T> ExpressionWithColumnType<T>.times(t: T): TimesOp<T, T> = TimesOp(this, wrap(t), columnType)

/** Multiplies this expression by the [other] expression. */
infix operator fun <T, S : T> ExpressionWithColumnType<T>.times(other: Expression<S>): TimesOp<T, S> = TimesOp(this, other, columnType)

// DIVIDE and REMAINDER

/** Divides this expression by the [t] value. */
infix operator fun <T> ExpressionWithColumnType<T>.div(t: T): DivideOp<T, T> = DivideOp(this, wrap(t), columnType)

/** Divides this expression by the [other] expression. */
infix operator fun <T, S : T> ExpressionWithColumnType<T>.div(other: Expression<S>): DivideOp<T, S> = DivideOp(this, other, columnType)

/** Calculates the remainder of dividing this expression by the [t] value. */
infix operator fun <T : Number?, S : T> ExpressionWithColumnType<T>.rem(t: S): ModOp<T, S, T> = ModOp<T, S, T>(this, wrap(t), columnType)

/** Calculates the remainder of dividing this expression by the [other] expression. */
infix operator fun <T : Number?, S : Number> ExpressionWithColumnType<T>.rem(other: Expression<S>): ModOp<T, S, T> = ModOp<T, S, T>(this, other, columnType)

/** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number. */
@JvmName("remWithEntityId")
infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(other: S): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> =
    ModOp(this, other)

/** Calculates the remainder of dividing [this] number expression by [other] numeric PK */
@JvmName("remWithEntityId2")
infix operator fun <T, S : Number, ID : EntityID<T>?> Expression<S>.rem(
    other: ExpressionWithColumnType<ID>
): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> =
    ModOp(this, other)

/** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number expression. */
@JvmName("remWithEntityId3")
infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(
    other: Expression<S>
): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> =
    ModOp(this, other)

/** Calculates the remainder of dividing this expression by the [t] value. */
infix fun <T : Number?, S : T> ExpressionWithColumnType<T>.mod(t: S): ModOp<T, S, T> = this % t

/** Calculates the remainder of dividing this expression by the [other] expression. */
infix fun <T : Number?, S : Number> ExpressionWithColumnType<T>.mod(other: Expression<S>): ModOp<T, S, T> = this % other

/** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number. */
@JvmName("modWithEntityId")
infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(
    other: S
): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> = this % other

/** Calculates the remainder of dividing [this] number expression by [other] numeric PK */
@JvmName("modWithEntityId2")
infix fun <T, S : Number, ID : EntityID<T>?> Expression<S>.mod(
    other: ExpressionWithColumnType<ID>
): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> = this % other

/** Calculates the remainder of dividing the value of [this] numeric PK by the [other] number expression. */
@JvmName("modWithEntityId3")
infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(other: Expression<S>): ExpressionWithColumnType<T> where T : Number, T : Comparable<T> =
    ModOp(this, other)

// Bitwise Operators

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

// Pattern Matching

/** Checks if this expression matches the specified [pattern]. */
infix fun <T : String?> Expression<T>.like(pattern: String): LikeEscapeOp = like(LikePattern(pattern))

/** Checks if this expression matches the specified [pattern]. */
infix fun <T : String?> Expression<T>.like(pattern: LikePattern): LikeEscapeOp =
    LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

/** Checks if this expression matches the specified [pattern]. */
@JvmName("likeWithEntityID")
infix fun Expression<EntityID<String>>.like(pattern: String): LikeEscapeOp = like(LikePattern(pattern))

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

/** Checks if this expression doesn't match the specified pattern. */
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

// Subquery Expressions

/** Returns an SQL operator that checks if [query] returns at least one row. */
fun exists(query: AbstractQuery<*>) = Exists(query)

/** Returns an SQL operator that checks if [query] doesn't returns any row. */
fun notExists(query: AbstractQuery<*>) = NotExists(query)

/** Checks if this expression is equals to any row returned from [query]. */
infix fun <T> Expression<T>.inSubQuery(query: AbstractQuery<*>): InSubQueryOp<T> = InSubQueryOp(this, query)

/** Checks if this expression is not equals to any row returned from [query]. */
infix fun <T> Expression<T>.notInSubQuery(query: AbstractQuery<*>): NotInSubQueryOp<T> = NotInSubQueryOp(this, query)

/** Checks if this expression is equals to single value returned from [query]. */
infix fun <T> Expression<T>.eqSubQuery(query: AbstractQuery<*>): EqSubQueryOp<T> = EqSubQueryOp(this, query)

/** Checks if this expression is not equals to single value returned from [query]. */
infix fun <T> Expression<T>.notEqSubQuery(query: AbstractQuery<*>): NotEqSubQueryOp<T> = NotEqSubQueryOp(this, query)

/** Checks if this expression is less than the single value returned from [query]. */
infix fun <T> Expression<T>.lessSubQuery(query: AbstractQuery<*>): LessSubQueryOp<T> = LessSubQueryOp(this, query)

/** Checks if this expression is less than or equal to the single value returned from [query]. */
infix fun <T> Expression<T>.lessEqSubQuery(query: AbstractQuery<*>): LessEqSubQueryOp<T> = LessEqSubQueryOp(this, query)

/** Checks if this expression is greater than the single value returned from [query]. */
infix fun <T> Expression<T>.greaterSubQuery(query: AbstractQuery<*>): GreaterSubQueryOp<T> = GreaterSubQueryOp(this, query)

/** Checks if this expression is greater than or equal to the single value returned from [query]. */
infix fun <T> Expression<T>.greaterEqSubQuery(query: AbstractQuery<*>): GreaterEqSubQueryOp<T> = GreaterEqSubQueryOp(this, query)

// Value Operators

/** Changes this integer expression to a [BigDecimal] type. */
fun ExpressionWithColumnType<Int>.intToDecimal(): NoOpConversion<Int, BigDecimal> =
    NoOpConversion(this, DecimalColumnType(precision = 15, scale = 0))

// Array/List/Table Comparisons

// ALL & ANY

/** Returns this subquery wrapped in the `ANY` operator. This function is not supported by the SQLite dialect. */
fun <T> anyFrom(subQuery: AbstractQuery<*>): Op<T> = AllAnyFromSubQueryOp(true, subQuery)

/**
 * Returns this array of data wrapped in the `ANY` operator. This function is only supported by PostgreSQL and H2 dialects.
 *
 * **Note** If [delegateType] is left `null`, the base column type associated with storing elements of type [T] will be
 * resolved according to the internal mapping of the element's type in [resolveColumnType].
 *
 * @param array Converted to a list view backed by the original array to improve performance. Do not mutate the array after passing to this function; if mutation is needed, convert using [Array.toList] instead.
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> anyFrom(array: Array<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyArray() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(true, array.asList(), columnType)
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
 * @param array Wrapped in the resulting [Op] to improve performance. Do not mutate after passing to this function, otherwise pass the list converted using [Array.toList].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> allFrom(array: Array<T>, delegateType: ColumnType<T>? = null): Op<T> {
    // emptyArray() without type info generates ARRAY[]
    @OptIn(InternalApi::class)
    val columnType = delegateType ?: resolveColumnType(T::class, if (array.isEmpty()) TextColumnType() else null)
    return AllAnyFromArrayOp(false, array.asList(), columnType)
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

// IN & NOT IN

/**
 * Checks if this expression is equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithSingleExpression01
 */
infix fun <T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): InListOrNotInListBaseOp<T> = SingleValueInListOp(this, list, isInList = true)

/**
 * Checks if expressions from this `Pair` are equal to elements from [list].
 * This syntax is unsupported by SQL Server.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithPairExpressions01
 **/
infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.inList(list: Iterable<Pair<T1, T2>>): InListOrNotInListBaseOp<Pair<T1, T2>> =
    PairInListOp(this, list, isInList = true)

/**
 * Checks if expressions from this `Triple` are equal to elements from [list].
 * This syntax is unsupported by SQL Server.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithTripleExpressions
 **/
infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.inList(
    list: Iterable<Triple<T1, T2, T3>>
): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
    TripleInListOp(this, list, isInList = true)

/**
 * Checks if all columns in this `List` are equal to any of the lists of values from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithMultipleColumns
 **/
infix fun List<Column<*>>.inList(list: Iterable<List<*>>): InListOrNotInListBaseOp<List<*>> =
    MultipleInListOp(this, list, isInList = true)

/**
 * Checks if all columns in this `List` are equal to any of the [CompositeID]s from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.CompositeIdTableEntityTest.testInListWithCompositeIdEntities
 **/
@Suppress("UNCHECKED_CAST")
@JvmName("inListCompositeIDs")
@LowPriorityInOverloadResolution
infix fun List<Column<*>>.inList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
    val componentList = list.map { id ->
        List(this.size) { i ->
            val component = id[this[i] as Column<Any>]
            component.takeIf { this[i].columnType is EntityIDColumnType<*> } ?: (component as EntityID<*>).value
        }
    }
    return this inList componentList
}

/**
 * Checks if this [EntityID] column is equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithEntityIDColumns
 */
@Suppress("UNCHECKED_CAST")
@JvmName("inListIds")
infix fun <T : Any, ID : EntityID<T>?> Column<ID>.inList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
    val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
    return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = true)
}

/**
 * Checks if this [EntityID] column is equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.CompositeIdTableEntityTest.testInListWithCompositeIdEntities
 */
@Suppress("UNCHECKED_CAST")
@JvmName("inListCompositeEntityIds")
infix fun <ID : EntityID<CompositeID>> Column<ID>.inList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
    val idTable = (columnType as EntityIDColumnType<CompositeID>).idColumn.table as CompositeIdTable
    return idTable.idColumns.toList() inList list
}

/**
 * Checks if this expression is not equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithSingleExpression01
 */
infix fun <T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<T> =
    SingleValueInListOp(this, list, isInList = false)

/**
 * Checks if expressions from this `Pair` are not equal to elements from [list].
 * This syntax is unsupported by SQL Server.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testNotInListWithPairExpressionsAndEmptyList
 **/
infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.notInList(
    list: Iterable<Pair<T1, T2>>
): InListOrNotInListBaseOp<Pair<T1, T2>> =
    PairInListOp(this, list, isInList = false)

/**
 * Checks if expressions from this `Triple` are not equal to elements from [list].
 * This syntax is unsupported by SQL Server.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithTripleExpressions
 **/
infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.notInList(
    list: Iterable<Triple<T1, T2, T3>>
): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
    TripleInListOp(this, list, isInList = false)

/**
 * Checks if all columns in this `List` are not equal to any of the lists of values from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithMultipleColumns
 **/
infix fun List<Column<*>>.notInList(list: Iterable<List<*>>): InListOrNotInListBaseOp<List<*>> =
    MultipleInListOp(this, list, isInList = false)

/**
 * Checks if all columns in this `List` are not equal to any of the [CompositeID]s from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.CompositeIdTableEntityTest.testInListWithCompositeIdEntities
 **/
@Suppress("UNCHECKED_CAST")
@JvmName("notInListCompositeIDs")
@LowPriorityInOverloadResolution
infix fun List<Column<*>>.notInList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
    val componentList = list.map { id ->
        List(this.size) { i ->
            val component = id[this[i] as Column<Any>]
            component.takeIf { this[i].columnType is EntityIDColumnType<*> } ?: (component as EntityID<*>).value
        }
    }
    return this notInList componentList
}

/**
 * Checks if this [EntityID] column is not equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testInListWithEntityIDColumns
 */
@Suppress("UNCHECKED_CAST")
@JvmName("notInListIds")
infix fun <T : Any, ID : EntityID<T>?> Column<ID>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
    val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
    return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = false)
}

/**
 * Checks if this [EntityID] column is not equal to any element from [list].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.CompositeIdTableEntityTest.testInListWithCompositeIdEntities
 */
@Suppress("UNCHECKED_CAST")
@JvmName("notInListCompositeEntityIds")
infix fun <ID : EntityID<CompositeID>> Column<ID>.notInList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
    val idTable = (columnType as EntityIDColumnType<CompositeID>).idColumn.table as CompositeIdTable
    return idTable.idColumns.toList() notInList list
}

// IN (TABLE ...)

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
