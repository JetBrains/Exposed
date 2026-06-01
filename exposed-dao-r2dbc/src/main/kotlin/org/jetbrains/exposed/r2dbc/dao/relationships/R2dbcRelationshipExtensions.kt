package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.reflect.KProperty

class SuspendReference<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    val reference: Column<REF>,
    val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    /**
     * Composite-FK child→parent column map (mirrors JDBC's `Reference.references`). `null` for
     * single-column references — [SuspendAccessor] falls back to `reference.referee`.
     */
    val references: Map<Column<*>, Column<*>>? = null
) {
    operator fun <SRC : R2dbcEntity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): SuspendAccessor<ID, Parent, REF> {
        return SuspendAccessor(reference, factory, thisRef, references)
    }
}

class OptionalSuspendReference<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    val reference: Column<REF?>,
    val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    /**
     * Composite-FK child→parent column map (mirrors JDBC's `OptionalReference.references`).
     */
    val references: Map<Column<*>, Column<*>>? = null
) {
    operator fun <SRC : R2dbcEntity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): OptionalSuspendAccessor<ID, Parent, REF> {
        return OptionalSuspendAccessor(reference, factory, thisRef, references)
    }
}

// TODO ALIGN_WITH_JDBC: `referencedOnSuspend` / `optionalReferencedOnSuspend` diverge from JDBC's
//  `referencedOn` / `optionalReferencedOn`. The suffix is intentional today (R2DBC returns a
//  suspend accessor while JDBC returns an entity directly), but a unified DSL across modules
//  would drop the suffix.
infix fun <ID : Any, Parent : R2dbcEntity<ID>, REF : Any> R2dbcEntityClass<ID, Parent>.referencedOnSuspend(
    reference: Column<REF>
): SuspendReference<ID, Parent, REF> {
    return SuspendReference(reference, this)
}

infix fun <ID : Any, Parent : R2dbcEntity<ID>, REF : Any> R2dbcEntityClass<ID, Parent>.optionalReferencedOnSuspend(
    reference: Column<REF?>
): OptionalSuspendReference<ID, Parent, REF> {
    return OptionalSuspendReference(reference, this)
}

/**
 * Composite-FK form of [referencedOnSuspend]. R2DBC counterpart of JDBC's `referencedOn(IdTable<*>)`.
 *
 * Resolves the composite foreign-key constraint on [table] that points at this entity's primary key
 * and binds the reference's first FK column as the delegate. (Full multi-column composite-FK
 * resolution isn't implemented yet — see `R2dbcEntityClass.kt` TODO ALIGN_WITH_JDBC.)
 */
@Suppress("UNCHECKED_CAST")
infix fun <ID : Any, Parent : R2dbcEntity<ID>> R2dbcEntityClass<ID, Parent>.referencedOnSuspend(
    table: IdTable<*>
): SuspendReference<ID, Parent, Any> {
    val tableFK = getCompositeForeignKey(table)
    val delegate = tableFK.from.first() as Column<Any>
    return SuspendReference(delegate, this, references = tableFK.references)
}

/**
 * Composite-FK form of [optionalReferencedOnSuspend]. R2DBC counterpart of JDBC's
 * `optionalReferencedOn(IdTable<*>)`.
 */
@Suppress("UNCHECKED_CAST")
infix fun <ID : Any, Parent : R2dbcEntity<ID>> R2dbcEntityClass<ID, Parent>.optionalReferencedOnSuspend(
    table: IdTable<*>
): OptionalSuspendReference<ID, Parent, Any> {
    val tableFK = getCompositeForeignKey(table)
    val delegate = tableFK.from.first() as Column<Any?>
    return OptionalSuspendReference(delegate, this, references = tableFK.references)
}
