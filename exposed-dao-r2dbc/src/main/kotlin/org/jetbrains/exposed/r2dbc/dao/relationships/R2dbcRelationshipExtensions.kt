package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.v1.core.Column
import kotlin.reflect.KProperty

class SuspendReference<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    val reference: Column<REF>,
    val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>
) {
    operator fun <SRC : R2dbcEntity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): SuspendAccessor<ID, Parent, REF> {
        return SuspendAccessor(reference, factory, thisRef)
    }
}

class OptionalSuspendReference<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    val reference: Column<REF?>,
    val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>
) {
    operator fun <SRC : R2dbcEntity<*>> provideDelegate(
        thisRef: SRC,
        property: KProperty<*>
    ): OptionalSuspendAccessor<ID, Parent, REF> {
        return OptionalSuspendAccessor(reference, factory, thisRef)
    }
}

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

fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF> R2dbcEntityClass<ChildID, Child>.referrersOnSuspend(
    reference: Column<REF>,
    cache: Boolean = true
): R2dbcReferrers<ParentID, Parent, ChildID, Child, REF> {
    return R2dbcReferrers(reference, this, cache)
}

infix fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF> R2dbcEntityClass<ChildID, Child>.referrersOnSuspend(
    reference: Column<REF>
): R2dbcReferrers<ParentID, Parent, ChildID, Child, REF> {
    return referrersOnSuspend(reference, cache = true)
}

fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF : Any>
    R2dbcEntityClass<ChildID, Child>.optionalReferrersOnSuspend(
        reference: Column<REF?>,
        cache: Boolean = true
    ): R2dbcReferrers<ParentID, Parent, ChildID, Child, REF?> {
    return R2dbcReferrers(reference, this, cache)
}

infix fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF : Any>
    R2dbcEntityClass<ChildID, Child>.optionalReferrersOnSuspend(reference: Column<REF?>): R2dbcReferrers<ParentID, Parent, ChildID, Child, REF?> {
    return optionalReferrersOnSuspend(reference, cache = true)
}

infix fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF>
    R2dbcEntityClass<ParentID, Parent>.backReferencedOnSuspend(reference: Column<REF>): R2dbcBackReference<ParentID, Parent, ChildID, Child, REF> {
    return R2dbcBackReference(reference, this)
}

infix fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF>
    R2dbcEntityClass<ParentID, Parent>.optionalBackReferencedOnSuspend(reference: Column<REF?>): R2dbcOptionalBackReference<ParentID, Parent, ChildID, Child, REF> {
    return R2dbcOptionalBackReference(reference, this)
}

/**
 * Overload for referencing a non-nullable column as an optional back reference.
 *
 * The child entity's referencing column is required (non-nullable) but, from the parent
 * side, the relationship is still optional: a child row may or may not exist. This mirrors
 * JDBC's `optionalBackReferencedOn(Column<REF>)` overload.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("optionalBackReferencedOnSuspendNonNullable")
infix fun <ParentID : Any, Parent : R2dbcEntity<ParentID>, ChildID : Any, Child : R2dbcEntity<ChildID>, REF : Any>
    R2dbcEntityClass<ParentID, Parent>.optionalBackReferencedOnSuspend(reference: Column<REF>): R2dbcOptionalBackReference<ParentID, Parent, ChildID, Child, REF> {
    return R2dbcOptionalBackReference(reference as Column<REF?>, this)
}
