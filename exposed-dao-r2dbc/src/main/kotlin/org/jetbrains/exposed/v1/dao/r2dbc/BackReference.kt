package org.jetbrains.exposed.v1.dao.r2dbc

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
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
 * What a one-to-one relationship declared with `backReferencedOn` gives back: the single child entity
 * that references this one, read by invoking the property.
 *
 * ```kotlin
 * class Film(id: EntityID<Int>) : IntEntity(id) {
 *     val review by Review backReferencedOn Reviews.film
 *
 *     companion object : IntEntityClass<Film>(Films)
 * }
 *
 * val stars = film.review().stars
 * ```
 *
 * The property is invoked rather than read directly because the lookup suspends.
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
 * What a one-to-one relationship declared with `optionalBackReferencedOn` gives back: the single child entity
 * that references this one, or `null` if there is none.
 *
 * ```kotlin
 * class Film(id: EntityID<Int>) : IntEntity(id) {
 *     val review by Review optionalBackReferencedOn Reviews.film
 *
 *     companion object : IntEntityClass<Film>(Films)
 * }
 *
 * val stars = film.review()?.stars
 * ```
 *
 * The property is invoked rather than read directly because the lookup suspends.
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
