package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

/**
 * Ensures the entity has a populated id before its back-reference is queried. JDBC handles
 * this implicitly via `DaoEntityID.invokeOnNoValue` from `thisRef.id.value`; R2DBC has to do
 * it as an explicit suspending step because DaoEntityID can't trigger `flushInserts`
 * (which is `suspend`) from a non-suspend getter.
 */
private suspend fun Entity<*>.ensureIdFlushed() {
    if (id._value != null) return
    TransactionManager.current().entityCache.flush()
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in a table
 * relation between two [Entity] classes, which retrieves the child entity that references the parent entity.
 *
 * R2DBC counterpart of JDBC's `BackReference` from `References.kt`. The delegate exposes the parent as a
 * `suspend () -> Parent` factory because the underlying lookup is suspending.
 *
 * @param reference The reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that references the parent entity.
 */
@ExperimentalR2dbcDaoApi
class BackReference<ParentID : Any, out Parent : Entity<ParentID>, ChildID : Any, in Child : Entity<ChildID>, REF>(
    reference: Column<REF>,
    factory: EntityClass<ParentID, Parent>,
    references: Map<Column<*>, Column<*>>? = null
) {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF>(
        reference,
        factory,
        cache = true,
        references = references
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent {
        val referrers = delegate.getValue(thisRef, property)

        return suspend {
            thisRef.ensureIdFlushed()
            referrers.single()
        }
    }
}

/**
 * Class responsible for implementing property delegates of the read-only properties involved in an optional table
 * relation between two [Entity] classes, which retrieves the child entity that optionally references the parent entity.
 *
 * R2DBC counterpart of JDBC's `OptionalBackReference` from `References.kt`. The delegate exposes the parent as a
 * `suspend () -> Parent?` factory because the underlying lookup is suspending.
 *
 * @param reference The nullable reference column defined on the child entity's associated table.
 * @param factory The [EntityClass] associated with the child entity that optionally references the parent entity.
 */
@ExperimentalR2dbcDaoApi
class OptionalBackReference<ParentID : Any, out Parent : Entity<ParentID>, ChildID : Any, in Child : Entity<ChildID>, REF>(
    reference: Column<REF?>,
    factory: EntityClass<ParentID, Parent>,
    references: Map<Column<*>, Column<*>>? = null
) {
    internal val delegate = Referrers<ChildID, Child, ParentID, Parent, REF?>(
        reference,
        factory,
        cache = true,
        references = references
    )

    operator fun getValue(thisRef: Child, property: KProperty<*>): suspend () -> Parent? {
        val referrers = delegate.getValue(thisRef, property)

        return suspend {
            thisRef.ensureIdFlushed()
            referrers.singleOrNull()
        }
    }
}
