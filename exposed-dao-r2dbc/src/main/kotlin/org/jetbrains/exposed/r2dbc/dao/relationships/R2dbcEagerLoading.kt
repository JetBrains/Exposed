package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.LazySizedIterable
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Eager-loads the specified [relations] for all entities in this [SizedIterable]. Mirrors JDBC's
 * `SizedIterable.with` — each direct relation is bulk-loaded via a single query instead of being
 * fetched lazily one entity at a time.
 *
 * Returns this [SizedIterable] to allow chaining; the loaded list is also pinned onto any
 * [LazySizedIterable] so subsequent iterations do not re-query the database.
 */
suspend fun <SRCID : Any, SRC : R2dbcEntity<SRCID>, REF : R2dbcEntity<*>, L : SizedIterable<SRC>> L.with(
    vararg relations: KProperty1<out REF, Any?>
): L {
    toList().apply {
        @Suppress("UNCHECKED_CAST")
        (this@with as? LazySizedIterable<SRC>)?.loadedResult = this
        if (any { it.isNewEntity() }) {
            TransactionManager.current().flushCache()
        }
        preloadRelations(*relations)
    }
    return this
}

/**
 * Eager-loads the specified [relations] for this entity. Mirrors JDBC's `Entity.load`.
 */
suspend fun <SRCID : Any, SRC : R2dbcEntity<SRCID>> SRC.load(
    vararg relations: KProperty1<out R2dbcEntity<*>, Any?>
): SRC = apply {
    SizedCollection(listOf(this)).with(*relations)
}

@Suppress("UNCHECKED_CAST", "ForbiddenComment")
private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadRelations(
    vararg relations: KProperty1<out R2dbcEntity<*>, Any?>,
    nodesVisited: MutableSet<R2dbcEntityClass<*, *>> = mutableSetOf()
) {
    val first = firstOrNull() ?: return
    if (!nodesVisited.add(first.klass)) return

    val directRelations = filterRelationsForEntity(first, relations)
    directRelations.forEach { prop ->
        when (val refObject = getReferenceObjectFromDelegatedProperty(first, prop)) {
            is SuspendAccessor<*, *, *> -> preloadReference(refObject as SuspendAccessor<Any, R2dbcEntity<Any>, Any>)
            is OptionalSuspendAccessor<*, *, *> ->
                preloadOptionalReference(refObject as OptionalSuspendAccessor<Any, R2dbcEntity<Any>, Any>)
            else -> {
                // TODO: extend preloading to cover Referrers / BackReference / OptionalBackReference
                //  / InnerTableLink delegates, like in JDBC's preloadRelations.
            }
        }
    }
}

/**
 * Bulk-loads parents referenced by a non-nullable [SuspendAccessor]-backed property, for every
 * entity in the receiver list. Loaded parents are inserted into the entity cache by the factory's
 * `find(...)` traversal (via `wrapRow`).
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadReference(
    accessor: SuspendAccessor<Any, R2dbcEntity<Any>, Any>
) {
    val reference = accessor.reference
    val factory = accessor.factory
    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }
    if (refIds.isEmpty()) return

    val referee = reference.referee ?: return
    val condition = buildInListCondition(referee, refIds.distinct())
    factory.find { condition }.toList()
}

/**
 * Bulk-loads parents referenced by an [OptionalSuspendAccessor]-backed property.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadOptionalReference(
    accessor: OptionalSuspendAccessor<Any, R2dbcEntity<Any>, Any>
) {
    val reference = accessor.reference as Column<Any?>
    val factory = accessor.factory
    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }
    if (refIds.isEmpty()) return

    val referee = reference.referee ?: return
    val condition = buildInListCondition(referee, refIds.distinct())
    factory.find { condition }.toList()
}

private fun R2dbcEntity<*>.lookupRefValue(reference: Column<*>): Any? {
    @Suppress("UNCHECKED_CAST")
    return writeValues[reference as Column<Any?>] ?: _readValues?.let { row -> row.getOrNull(reference) }
}

@Suppress("UNCHECKED_CAST")
private fun buildInListCondition(referee: Column<*>, refIds: List<Any>): org.jetbrains.exposed.v1.core.Op<Boolean> {
    // If the reference column stores a raw value while the referee is an EntityID column (or vice
    // versa), normalise the types before building the IN (...) expression.
    val baseColumn = referee.takeUnless {
        it.columnType is EntityIDColumnType<*> && refIds.first() !is EntityID<*>
    } ?: (referee.columnType as EntityIDColumnType<Any>).idColumn
    return (baseColumn as Column<Any>) inList refIds
}

private fun <SRC : R2dbcEntity<*>> filterRelationsForEntity(
    entity: SRC,
    relations: Array<out KProperty1<out R2dbcEntity<*>, Any?>>
): Collection<KProperty1<SRC, Any?>> {
    val validMembers = entity::class.memberProperties
    @Suppress("UNCHECKED_CAST")
    return validMembers.filter { it in relations } as Collection<KProperty1<SRC, Any?>>
}

private fun <SRC : R2dbcEntity<*>> getReferenceObjectFromDelegatedProperty(
    entity: SRC,
    property: KProperty1<SRC, Any?>
): Any? {
    property.isAccessible = true
    return property.getDelegate(entity)
}
