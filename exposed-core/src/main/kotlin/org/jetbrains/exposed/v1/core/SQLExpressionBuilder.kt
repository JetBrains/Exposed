@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

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
import org.jetbrains.exposed.v1.core.inList as topLevelInList
import org.jetbrains.exposed.v1.core.notInList as topLevelNotInList
import org.jetbrains.exposed.v1.core.rem as topLevelRem
import org.jetbrains.exposed.v1.core.wrap as topLevelWrap

@Deprecated(
    message = "This builder interface will continue to be phased out following release 1.0.0. " +
        "All expression builder methods previously restricted to this interface have also been deprecated in favor of " +
        "equivalent top-level functions, making implementations of this interface useless as a receiver in any scope. " +
        "It will no longer be necessary to import each individual method when used outside a scoped block, " +
        "and on demand imports will now be possible via 'import org.jetbrains.exposed.v1.core.*', if required.",
    level = DeprecationLevel.WARNING
)
@Suppress("INAPPLICABLE_JVM_NAME", "TooManyFunctions", "LargeClass")
interface ISqlExpressionBuilder {
    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq t", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.eq(t: T): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq t", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> CompositeColumn<T>.eq(t: T): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq other", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq t", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("eqEntityIDValue")
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(t: V): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq other", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.eq(
        other: Expression<V>
    ): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eq other", "org.jetbrains.exposed.v1.core.eq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.eq(
        other: ExpressionWithColumnType<E>
    ): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this neq other", "org.jetbrains.exposed.v1.core.neq"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this neq other", "org.jetbrains.exposed.v1.core.neq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this neq t", "org.jetbrains.exposed.v1.core.neq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("neqEntityIDValue")
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(t: V): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this neq other", "org.jetbrains.exposed.v1.core.neq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.neq(
        other: Expression<V>
    ): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this neq other", "org.jetbrains.exposed.v1.core.neq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.neq(
        other: ExpressionWithColumnType<E>
    ): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less t", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.less(t: T): LessOp = LessOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less other", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.less(other: Expression<in S>): LessOp = LessOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less t", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("lessEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.less(t: T): LessOp =
        LessOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less other", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.less(
        other: Expression<in V>
    ): LessOp = LessOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less other", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.less(
        other: ExpressionWithColumnType<E>
    ): LessOp = LessOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this less other", "org.jetbrains.exposed.v1.core.less"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("lessBetweenEntityIDs")
    infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.less(
        other: Expression<E>
    ): LessOp = LessOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("thie lessEq t", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.lessEq(t: T): LessEqOp = LessEqOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEq other", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.lessEq(other: Expression<in S>): LessEqOp = LessEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEq t", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("lessEqEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.lessEq(t: T): LessEqOp =
        LessEqOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEq other", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.lessEq(
        other: Expression<in V>
    ): LessEqOp = LessEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEq other", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.lessEq(
        other: ExpressionWithColumnType<E>
    ): LessEqOp = LessEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEq other", "org.jetbrains.exposed.v1.core.lessEq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("lessEqBetweenEntityIDs")
    infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.lessEq(
        other: Expression<E>
    ): LessEqOp = LessEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater t", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greater(t: T): GreaterOp = GreaterOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater other", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greater(other: Expression<in S>): GreaterOp = GreaterOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater t", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("greaterEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.greater(t: T): GreaterOp =
        GreaterOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater other", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.greater(
        other: Expression<in V>
    ): GreaterOp = GreaterOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater other", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.greater(
        other: ExpressionWithColumnType<E>
    ): GreaterOp = GreaterOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greater other", "org.jetbrains.exposed.v1.core.greater"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("greaterBetweenEntityIDs")
    infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.greater(
        other: Expression<E>
    ): GreaterOp = GreaterOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq t", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.greaterEq(t: T): GreaterEqOp = GreaterEqOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq other", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.greaterEq(other: Expression<in S>): GreaterEqOp = GreaterEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq t", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("greaterEqEntityID")
    infix fun <T : Comparable<T>> Column<EntityID<T>>.greaterEq(t: T): GreaterEqOp =
        GreaterEqOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq other", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.greaterEq(
        other: Expression<in V>
    ): GreaterEqOp = GreaterEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq other", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, V : T?, E : EntityID<T>?> Expression<V>.greaterEq(
        other: ExpressionWithColumnType<E>
    ): GreaterEqOp = GreaterEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEq other", "org.jetbrains.exposed.v1.core.greaterEq"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("greaterEqBetweenEntityIDs")
    infix fun <T : Comparable<T>, E : EntityID<T>?> Expression<E>.greaterEq(
        other: Expression<E>
    ): GreaterEqOp = GreaterEqOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("between(from, to)", "org.jetbrains.exposed.v1.core.between"),
        level = DeprecationLevel.ERROR
    )
    fun <T, S : T?> ExpressionWithColumnType<in S>.between(from: T, to: T): Between = Between(this, topLevelWrap(from), topLevelWrap(to))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("between(from, to)", "org.jetbrains.exposed.v1.core.between"),
        level = DeprecationLevel.ERROR
    )
    fun <T : Any, E : EntityID<T>?> Column<E>.between(from: T, to: T): Between =
        Between(this, topLevelWrap(EntityID(from, this.idTable())), topLevelWrap(EntityID(to, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("isNull()", "org.jetbrains.exposed.v1.core.isNull"),
        level = DeprecationLevel.ERROR
    )
    fun <T> Expression<T>.isNull(): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("isNullOrEmpty()", "org.jetbrains.exposed.v1.core.isNullOrEmpty"),
        level = DeprecationLevel.ERROR
    )
    fun <T : String?> Expression<T>.isNullOrEmpty(): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("isNotNull()", "org.jetbrains.exposed.v1.core.isNotNull"),
        level = DeprecationLevel.ERROR
    )
    fun <T> Expression<T>.isNotNull(): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isNotDistinctFrom t", "org.jetbrains.exposed.v1.core.isNotDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isNotDistinctFrom(t: T): IsNotDistinctFromOp = IsNotDistinctFromOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isNotDistinctFrom other", "org.jetbrains.exposed.v1.core.isNotDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.isNotDistinctFrom(other: Expression<in S>): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isNotDistinctFrom t", "org.jetbrains.exposed.v1.core.isNotDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("isNotDistinctFromEntityID")
    infix fun <T : Any> Column<EntityID<T>>.isNotDistinctFrom(t: T): IsNotDistinctFromOp =
        IsNotDistinctFromOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isNotDistinctFrom other", "org.jetbrains.exposed.v1.core.isNotDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isNotDistinctFrom(
        other: Expression<in V>
    ): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isNotDistinctFrom other", "org.jetbrains.exposed.v1.core.isNotDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<V>.isNotDistinctFrom(
        other: ExpressionWithColumnType<E>
    ): IsNotDistinctFromOp = IsNotDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isDistinctFrom t", "org.jetbrains.exposed.v1.core.isDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    @LowPriorityInOverloadResolution
    infix fun <T : Comparable<T>, S : T?> ExpressionWithColumnType<in S>.isDistinctFrom(t: T): IsDistinctFromOp = IsDistinctFromOp(this, topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isDistinctFrom other", "org.jetbrains.exposed.v1.core.isDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Comparable<T>, S : T?> Expression<in S>.isDistinctFrom(other: Expression<in S>): IsDistinctFromOp = IsDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isDistinctFrom t", "org.jetbrains.exposed.v1.core.isDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("isDistinctFromEntityID")
    infix fun <T : Any> Column<EntityID<T>>.isDistinctFrom(t: T): IsDistinctFromOp =
        IsDistinctFromOp(this, topLevelWrap(EntityID(t, this.idTable())))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isDistinctFrom other", "org.jetbrains.exposed.v1.core.isDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, E : EntityID<T>?, V : T?> ExpressionWithColumnType<E>.isDistinctFrom(
        other: Expression<in V>
    ): IsDistinctFromOp = IsDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this isDistinctFrom other", "org.jetbrains.exposed.v1.core.isDistinctFrom"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Any, V : T?, E : EntityID<T>?> Expression<in V>.isDistinctFrom(
        other: ExpressionWithColumnType<E>
    ): IsDistinctFromOp = IsDistinctFromOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this + t", "org.jetbrains.exposed.v1.core.plus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T> ExpressionWithColumnType<T>.plus(t: T): PlusOp<T, T> = PlusOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this + other", "org.jetbrains.exposed.v1.core.plus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.plus(other: Expression<S>): PlusOp<T, S> = PlusOp(this, other, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this + value", "org.jetbrains.exposed.v1.core.plus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun Expression<String>.plus(value: String): Concat = Concat("", this, stringLiteral(value))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this + value", "org.jetbrains.exposed.v1.core.plus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun Expression<String>.plus(value: Expression<String>): Concat = Concat("", this, value)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this + value", "org.jetbrains.exposed.v1.core.plus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun String.plus(value: Expression<String>): Concat = Concat("", stringLiteral(this), value)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this - t", "org.jetbrains.exposed.v1.core.minus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T> ExpressionWithColumnType<T>.minus(t: T): MinusOp<T, T> = MinusOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this - other", "org.jetbrains.exposed.v1.core.minus"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.minus(other: Expression<S>): MinusOp<T, S> = MinusOp(this, other, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this * t", "org.jetbrains.exposed.v1.core.times"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T> ExpressionWithColumnType<T>.times(t: T): TimesOp<T, T> = TimesOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this * other", "org.jetbrains.exposed.v1.core.times"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.times(other: Expression<S>): TimesOp<T, S> = TimesOp(this, other, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this / t", "org.jetbrains.exposed.v1.core.div"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T> ExpressionWithColumnType<T>.div(t: T): DivideOp<T, T> = DivideOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this / other", "org.jetbrains.exposed.v1.core.div"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T, S : T> ExpressionWithColumnType<T>.div(other: Expression<S>): DivideOp<T, S> = DivideOp(this, other, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this % t", "org.jetbrains.exposed.v1.core.rem"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T : Number?, S : T> ExpressionWithColumnType<T>.rem(t: S) = ModOp<T, S, T>(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this % other", "org.jetbrains.exposed.v1.core.rem"),
        level = DeprecationLevel.ERROR
    )
    infix operator fun <T : Number?, S : Number> ExpressionWithColumnType<T>.rem(other: Expression<S>) = ModOp<T, S, T>(this, other, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this % other", "org.jetbrains.exposed.v1.core.rem"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("remWithEntityId")
    infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(other: S) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this % other", "org.jetbrains.exposed.v1.core.rem"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("remWithEntityId2")
    infix operator fun <T, S : Number, ID : EntityID<T>?> Expression<S>.rem(other: ExpressionWithColumnType<ID>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this % other", "org.jetbrains.exposed.v1.core.rem"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("remWithEntityId3")
    infix operator fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.rem(other: Expression<S>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this mod t", "org.jetbrains.exposed.v1.core.mod"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Number?, S : T> ExpressionWithColumnType<T>.mod(t: S) = this topLevelRem t

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this mod other", "org.jetbrains.exposed.v1.core.mod"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : Number?, S : Number> ExpressionWithColumnType<T>.mod(other: Expression<S>) = this topLevelRem other

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this mod other", "org.jetbrains.exposed.v1.core.mod"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("modWithEntityId")
    infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(other: S) where T : Number, T : Comparable<T> = this topLevelRem other

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this mod other", "org.jetbrains.exposed.v1.core.mod"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("modWithEntityId2")
    infix fun <T, S : Number, ID : EntityID<T>?> Expression<S>.mod(other: ExpressionWithColumnType<ID>) where T : Number, T : Comparable<T> = this topLevelRem other

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this mod other", "org.jetbrains.exposed.v1.core.mod"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("modWithEntityId3")
    infix fun <T, S : Number, ID : EntityID<T>?> ExpressionWithColumnType<ID>.mod(other: Expression<S>) where T : Number, T : Comparable<T> =
        ModOp(this, other)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseAnd t", "org.jetbrains.exposed.v1.core.bitwiseAnd"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseAnd(t: T): AndBitOp<T, T> = AndBitOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseAnd t", "org.jetbrains.exposed.v1.core.bitwiseAnd"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseAnd(t: Expression<T>): AndBitOp<T, T> = AndBitOp(this, t, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseOr t", "org.jetbrains.exposed.v1.core.bitwiseOr"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseOr(t: T): OrBitOp<T, T> = OrBitOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseOr t", "org.jetbrains.exposed.v1.core.bitwiseOr"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseOr(t: Expression<T>): OrBitOp<T, T> = OrBitOp(this, t, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseXor t", "org.jetbrains.exposed.v1.core.bitwiseXor"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseXor(t: T): XorBitOp<T, T> = XorBitOp(this, topLevelWrap(t), columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this bitwiseXor t", "org.jetbrains.exposed.v1.core.bitwiseXor"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.bitwiseXor(t: Expression<T>): XorBitOp<T, T> = XorBitOp(this, t, columnType)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this hasFlag t", "org.jetbrains.exposed.v1.core.hasFlag"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.hasFlag(t: T): EqOp = EqOp(AndBitOp(this, topLevelWrap(t), columnType), topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this hasFlag t", "org.jetbrains.exposed.v1.core.hasFlag"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.hasFlag(t: Expression<T>): EqOp = EqOp(AndBitOp(this, t, columnType), topLevelWrap(t))

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("concat(*expr)", "org.jetbrains.exposed.v1.core.concat"),
        level = DeprecationLevel.ERROR
    )
    fun concat(vararg expr: Expression<*>): Concat = Concat("", *expr)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("concat(separator, expr)", "org.jetbrains.exposed.v1.core.concat"),
        level = DeprecationLevel.ERROR
    )
    fun concat(separator: String = "", expr: List<Expression<*>>): Concat = Concat(separator, expr = expr.toTypedArray())

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like pattern", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.like(pattern: String): LikeEscapeOp {
        val likePattern = LikePattern(pattern)
        return LikeEscapeOp(this, stringParam(likePattern.pattern), true, likePattern.escapeChar)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like pattern", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.like(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like pattern", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: String): LikeEscapeOp {
        val likePattern = LikePattern(pattern)
        return LikeEscapeOp(this, stringParam(likePattern.pattern), true, likePattern.escapeChar)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like pattern", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("likeWithEntityID")
    infix fun Expression<EntityID<String>>.like(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), true, pattern.escapeChar)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like expression", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, true, null)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this like expression", "org.jetbrains.exposed.v1.core.like"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("likeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.like(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, true, null)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this match pattern", "org.jetbrains.exposed.v1.core.match"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.match(pattern: String): Op<Boolean> = Op.TRUE

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("match(pattern, mode)", "org.jetbrains.exposed.v1.core.match"),
        level = DeprecationLevel.ERROR
    )
    fun <T : String?> Expression<T>.match(
        pattern: String,
        mode: FunctionProvider.MatchMode?
    ): Op<Boolean> = with(currentDialect.functionProvider) { this@match.match(pattern, mode) }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike pattern", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.notLike(pattern: String): LikeEscapeOp {
        val notLikePattern = LikePattern(pattern)
        return LikeEscapeOp(this, stringParam(notLikePattern.pattern), false, notLikePattern.escapeChar)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike pattern", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.notLike(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike pattern", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: String): LikeEscapeOp {
        val notLikePattern = LikePattern(pattern)
        return LikeEscapeOp(this, stringParam(notLikePattern.pattern), false, notLikePattern.escapeChar)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike pattern", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("notLikeWithEntityID")
    infix fun Expression<EntityID<String>>.notLike(pattern: LikePattern): LikeEscapeOp =
        LikeEscapeOp(this, stringParam(pattern.pattern), false, pattern.escapeChar)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike expression", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, false, null)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notLike expression", "org.jetbrains.exposed.v1.core.notLike"),
        level = DeprecationLevel.ERROR
    )
    @JvmName("notLikeWithEntityIDAndExpression")
    infix fun Expression<EntityID<String>>.notLike(expression: ExpressionWithColumnType<String>): LikeEscapeOp =
        LikeEscapeOp(this, expression, false, null)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this regexp pattern", "org.jetbrains.exposed.v1.core.regexp"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T : String?> Expression<T>.regexp(pattern: String): RegexpOp<T> = RegexpOp(this, stringParam(pattern), true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("regexp(pattern, caseSensitive)", "org.jetbrains.exposed.v1.core.regexp"),
        level = DeprecationLevel.ERROR
    )
    fun <T : String?> Expression<T>.regexp(
        pattern: Expression<String>,
        caseSensitive: Boolean = true
    ): RegexpOp<T> = RegexpOp(this, pattern, caseSensitive)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("rowNumber()", "org.jetbrains.exposed.v1.core.rowNumber"),
        level = DeprecationLevel.ERROR
    )
    fun rowNumber(): RowNumber = RowNumber()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("rank()", "org.jetbrains.exposed.v1.core.rank"),
        level = DeprecationLevel.ERROR
    )
    fun rank(): Rank = Rank()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("denseRank()", "org.jetbrains.exposed.v1.core.denseRank"),
        level = DeprecationLevel.ERROR
    )
    fun denseRank(): DenseRank = DenseRank()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("percentRank()", "org.jetbrains.exposed.v1.core.percentRank"),
        level = DeprecationLevel.ERROR
    )
    fun percentRank(): PercentRank = PercentRank()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("cumeDist()", "org.jetbrains.exposed.v1.core.cumeDist"),
        level = DeprecationLevel.ERROR
    )
    fun cumeDist(): CumeDist = CumeDist()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("ntile(numBuckets)", "org.jetbrains.exposed.v1.core.ntile"),
        level = DeprecationLevel.ERROR
    )
    fun ntile(numBuckets: ExpressionWithColumnType<Int>): Ntile = Ntile(numBuckets)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("lag(offset, defaultValue)", "org.jetbrains.exposed.v1.core.lag"),
        level = DeprecationLevel.ERROR
    )
    fun <T> ExpressionWithColumnType<T>.lag(
        offset: ExpressionWithColumnType<Int> = intLiteral(1),
        defaultValue: ExpressionWithColumnType<T>? = null
    ): Lag<T> = Lag(this, offset, defaultValue)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("lead(offset, defaultValue)", "org.jetbrains.exposed.v1.core.lead"),
        level = DeprecationLevel.ERROR
    )
    fun <T> ExpressionWithColumnType<T>.lead(
        offset: ExpressionWithColumnType<Int> = intLiteral(1),
        defaultValue: ExpressionWithColumnType<T>? = null
    ): Lead<T> = Lead(this, offset, defaultValue)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("firstValue()", "org.jetbrains.exposed.v1.core.firstValue"),
        level = DeprecationLevel.ERROR
    )
    fun <T> ExpressionWithColumnType<T>.firstValue(): FirstValue<T> = FirstValue(this)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("lastValue()", "org.jetbrains.exposed.v1.core.lastValue"),
        level = DeprecationLevel.ERROR
    )
    fun <T> ExpressionWithColumnType<T>.lastValue(): LastValue<T> = LastValue(this)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("nthValue(n)", "org.jetbrains.exposed.v1.core.nthValue"),
        level = DeprecationLevel.ERROR
    )
    fun <T> ExpressionWithColumnType<T>.nthValue(n: ExpressionWithColumnType<Int>): NthValue<T> = NthValue(this, n)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("coalesce(expr, alternate, *others)", "org.jetbrains.exposed.v1.core.coalesce"),
        level = DeprecationLevel.ERROR
    )
    fun <T, S : T?> coalesce(
        expr: ExpressionWithColumnType<S>,
        alternate: Expression<out T>,
        vararg others: Expression<out T>
    ): Coalesce<T, S> = Coalesce(expr, alternate, others = others)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("case()", "org.jetbrains.exposed.v1.core.case"),
        level = DeprecationLevel.ERROR
    )
    fun case(): Case = Case()

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("case(value)", "org.jetbrains.exposed.v1.core.case"),
        level = DeprecationLevel.ERROR
    )
    fun <T> case(value: ExpressionWithColumnType<T>): ValueCase<T> = ValueCase(value)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inSubQuery query", "org.jetbrains.exposed.v1.core.inSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.inSubQuery(query: AbstractQuery<*>): InSubQueryOp<T> = InSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInSubQuery query", "org.jetbrains.exposed.v1.core.notInSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.notInSubQuery(query: AbstractQuery<*>): NotInSubQueryOp<T> = NotInSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this eqSubQuery query", "org.jetbrains.exposed.v1.core.eqSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.eqSubQuery(query: AbstractQuery<*>): EqSubQueryOp<T> = EqSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notEqSubQuery query", "org.jetbrains.exposed.v1.core.notEqSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.notEqSubQuery(query: AbstractQuery<*>): NotEqSubQueryOp<T> = NotEqSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessSubQuery query", "org.jetbrains.exposed.v1.core.lessSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.lessSubQuery(query: AbstractQuery<*>): LessSubQueryOp<T> = LessSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this lessEqSubQuery query", "org.jetbrains.exposed.v1.core.lessEqSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.lessEqSubQuery(query: AbstractQuery<*>): LessEqSubQueryOp<T> = LessEqSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterSubQuery query", "org.jetbrains.exposed.v1.core.greaterSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.greaterSubQuery(query: AbstractQuery<*>): GreaterSubQueryOp<T> = GreaterSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this greaterEqSubQuery query", "org.jetbrains.exposed.v1.core.greaterEqSubQuery"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> Expression<T>.greaterEqSubQuery(query: AbstractQuery<*>): GreaterEqSubQueryOp<T> = GreaterEqSubQueryOp(this, query)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): InListOrNotInListBaseOp<T> = SingleValueInListOp(this, list, isInList = true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.inList(list: Iterable<Pair<T1, T2>>): InListOrNotInListBaseOp<Pair<T1, T2>> =
        PairInListOp(this, list, isInList = true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.inList(
        list: Iterable<Triple<T1, T2, T3>>
    ): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    infix fun List<Column<*>>.inList(list: Iterable<List<*>>): InListOrNotInListBaseOp<List<*>> =
        MultipleInListOp(this, list, isInList = true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
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
        return this topLevelInList componentList
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun <T : Any, ID : EntityID<T>?> Column<ID>.inList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = true)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inList list", "org.jetbrains.exposed.v1.core.inList"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    @JvmName("inListCompositeEntityIds")
    infix fun <ID : EntityID<CompositeID>> Column<ID>.inList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
        val idTable = (columnType as EntityIDColumnType<CompositeID>).idColumn.table as CompositeIdTable
        return idTable.idColumns.toList() topLevelInList list
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<T> =
        SingleValueInListOp(this, list, isInList = false)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T1, T2> Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>.notInList(
        list: Iterable<Pair<T1, T2>>
    ): InListOrNotInListBaseOp<Pair<T1, T2>> =
        PairInListOp(this, list, isInList = false)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T1, T2, T3> Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>.notInList(
        list: Iterable<Triple<T1, T2, T3>>
    ): InListOrNotInListBaseOp<Triple<T1, T2, T3>> =
        TripleInListOp(this, list, isInList = false)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    infix fun List<Column<*>>.notInList(list: Iterable<List<*>>): InListOrNotInListBaseOp<List<*>> =
        MultipleInListOp(this, list, isInList = false)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
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
        return this topLevelNotInList componentList
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListIds")
    infix fun <T : Any, ID : EntityID<T>?> Column<ID>.notInList(list: Iterable<T>): InListOrNotInListBaseOp<EntityID<T>?> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return SingleValueInListOp(this, list.map { EntityIDFunctionProvider.createEntityID(it, idTable) }, isInList = false)
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInList list", "org.jetbrains.exposed.v1.core.notInList"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListCompositeEntityIds")
    infix fun <ID : EntityID<CompositeID>> Column<ID>.notInList(list: Iterable<CompositeID>): InListOrNotInListBaseOp<List<*>> {
        val idTable = (columnType as EntityIDColumnType<CompositeID>).idColumn.table as CompositeIdTable
        return idTable.idColumns.toList() topLevelNotInList list
    }

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this inTable table", "org.jetbrains.exposed.v1.core.inTable"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.inTable(table: Table): InTableOp = InTableOp(this, table, true)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("this notInTable table", "org.jetbrains.exposed.v1.core.notInTable"),
        level = DeprecationLevel.ERROR
    )
    infix fun <T> ExpressionWithColumnType<T>.notInTable(table: Table): InTableOp = InTableOp(this, table, false)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("wrap(value)", "org.jetbrains.exposed.v1.core.wrap"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST")
    fun <T, S : T?> ExpressionWithColumnType<in S>.wrap(value: T): QueryParameter<T> =
        QueryParameter(value, columnType as IColumnType<T & Any>)

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("asLiteral(value)", "org.jetbrains.exposed.v1.core.asLiteral"),
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    fun <T, S : T?> ExpressionWithColumnType<S>.asLiteral(value: T): LiteralOp<T> = when {
        value is ByteArray && columnType is BasicBinaryColumnType -> stringLiteral(value.toString(Charsets.UTF_8))
        columnType is ColumnWithTransform<*, *> -> (columnType as ColumnWithTransform<Any, Any>)
            .let { LiteralOp(it.originalColumnType, it.unwrapRecursive(value)) }
        else -> LiteralOp(columnType as IColumnType<T & Any>, value)
    } as LiteralOp<T>

    @Deprecated(
        message = "This interface method will be removed following release 1.0.0 and should be replaced with the equivalent top-level function.",
        replaceWith = ReplaceWith("intToDecimal()", "org.jetbrains.exposed.v1.core.intToDecimal"),
        level = DeprecationLevel.ERROR
    )
    fun ExpressionWithColumnType<Int>.intToDecimal(): NoOpConversion<Int, BigDecimal> =
        NoOpConversion(this, DecimalColumnType(precision = 15, scale = 0))

    private fun <T : Any, E : EntityID<T>> Column<out E?>.idTable(): IdTable<T> =
        when (val table = this.foreignKey?.targetTable ?: this.table) {
            is Alias<*> -> table.delegate
            else -> table
        } as IdTable<T>
}

@Deprecated(
    message = "This builder object will continue to be phased out following release 1.0.0. " +
        "All expression builder methods previously restricted to this object have also been deprecated in favor of " +
        "equivalent top-level functions, making this object useless as a receiver in any scope. " +
        "It will no longer be necessary to import each individual method when used outside a scoped block, " +
        "and on demand imports will now be possible via 'import org.jetbrains.exposed.v1.core.*', if required.",
    level = DeprecationLevel.ERROR
)
object SqlExpressionBuilder : ISqlExpressionBuilder
