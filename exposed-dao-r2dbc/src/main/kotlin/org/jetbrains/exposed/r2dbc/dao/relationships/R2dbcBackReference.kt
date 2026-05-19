package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

/**
 * Ensures the entity has a populated id before its back-reference is queried. JDBC handles
 * this implicitly via `DaoEntityID.invokeOnNoValue` from `thisRef.id.value`; R2DBC has to do
 * it as an explicit suspending step because R2dbcDaoEntityID can't trigger `flushInserts`
 * (which is `suspend`) from a non-suspend getter.
 */
private suspend fun R2dbcEntity<*>.ensureIdFlushed() {
    if (id._value != null) return
    TransactionManager.current().entityCache.flush()
}

class R2dbcBackReference<ParentID : Any, out Parent : R2dbcEntity<ParentID>, ChildID : Any, in Child : R2dbcEntity<ChildID>, REF>(
    reference: Column<REF>,
    factory: R2dbcEntityClass<ParentID, Parent>
) {
    internal val delegate = R2dbcReferrers<ChildID, Child, ParentID, Parent, REF>(
        reference,
        factory,
        cache = true
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent {
        val referrersLambda = delegate.getValue(thisRef, property)

        return suspend {
            thisRef.ensureIdFlushed()
            referrersLambda().single()
        }
    }
}

class R2dbcOptionalBackReference<ParentID : Any, out Parent : R2dbcEntity<ParentID>, ChildID : Any, in Child : R2dbcEntity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: R2dbcEntityClass<ParentID, Parent>
) {
    internal val delegate = R2dbcReferrers<ChildID, Child, ParentID, Parent, REF?>(
        reference,
        factory,
        cache = true
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent? {
        val referrersLambda = delegate.getValue(thisRef, property)

        return suspend {
            thisRef.ensureIdFlushed()
            referrersLambda().singleOrNull()
        }
    }
}
