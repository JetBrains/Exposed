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
