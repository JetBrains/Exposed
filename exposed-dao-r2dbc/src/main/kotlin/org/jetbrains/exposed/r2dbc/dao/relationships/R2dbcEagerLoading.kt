package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.LazySizedIterable
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.select
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
    val loadedByRelation = mutableListOf<R2dbcEntity<*>>()

    directRelations.forEach { prop ->
        val loaded: List<R2dbcEntity<*>> = when (val refObject = getReferenceObjectFromDelegatedProperty(first, prop)) {
            is SuspendAccessor<*, *, *> ->
                preloadReference(refObject as SuspendAccessor<Any, R2dbcEntity<Any>, Any>)
            is OptionalSuspendAccessor<*, *, *> ->
                preloadOptionalReference(refObject as OptionalSuspendAccessor<Any, R2dbcEntity<Any>, Any>)
            is R2dbcReferrers<*, *, *, *, *> ->
                preloadReferrers(refObject as R2dbcReferrers<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>, Any>)
            is R2dbcInnerTableLinkAccessor<*, *, *, *> ->
                preloadInnerTableLink(refObject as R2dbcInnerTableLinkAccessor<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>>)
            is R2dbcBackReference<*, *, *, *, *> ->
                preloadReferrers(refObject.delegate as R2dbcReferrers<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>, Any>)
            is R2dbcOptionalBackReference<*, *, *, *, *> ->
                preloadReferrers(refObject.delegate as R2dbcReferrers<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>, Any>)
            else -> emptyList()
        }
        loadedByRelation += loaded
    }

    // Mirrors JDBC's recursive step in `preloadRelations`.
    if (directRelations.isNotEmpty() && relations.size != directRelations.size) {
        val remainingRelations = (relations.toList() - directRelations.toSet()).toTypedArray()
        loadedByRelation.groupBy { it::class }.forEach { (_, entities) ->
            (entities as List<R2dbcEntity<Any>>).preloadRelations(
                relations = remainingRelations,
                nodesVisited = nodesVisited
            )
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
): List<R2dbcEntity<*>> {
    val reference = accessor.reference
    val factory = accessor.factory
    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }
    if (refIds.isEmpty()) return emptyList()

    val referee = reference.referee ?: return emptyList()
    val condition = buildInListCondition(referee, refIds.distinct())
    return factory.find { condition }.toList()
}

/**
 * Bulk-loads parents referenced by an [OptionalSuspendAccessor]-backed property.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadOptionalReference(
    accessor: OptionalSuspendAccessor<Any, R2dbcEntity<Any>, Any>
): List<R2dbcEntity<*>> {
    val reference = accessor.reference as Column<Any?>
    val factory = accessor.factory
    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }
    if (refIds.isEmpty()) return emptyList()

    val referee = reference.referee ?: return emptyList()
    val condition = buildInListCondition(referee, refIds.distinct())
    return factory.find { condition }.toList()
}

/**
 * Bulk-loads child entities for a one-to-many relationship (`R2dbcReferrers`-backed property),
 * for every parent in the receiver list. Children are grouped by their reference column value
 * and inserted into the entity cache's referrers slot — so subsequent calls to the parent's
 * accessor (`parent.children()`) hit the cache without issuing per-parent queries.
 *
 * Mirrors JDBC's `warmUpReferences` for the simple `EntityIDColumnType` case.
 */
private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadReferrers(
    referrers: R2dbcReferrers<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>, Any>
): List<R2dbcEntity<*>> {
    val refColumn = referrers.reference
    val factory = referrers.factory

    val referee = refColumn.referee ?: return emptyList()

    val parentMappings: List<Pair<EntityID<*>, Any>> = mapNotNull { entity ->
        if (entity.id._value == null) return@mapNotNull null
        val refereeValue = entity.lookupRefValue(referee) ?: return@mapNotNull null
        entity.id to refereeValue
    }
    if (parentMappings.isEmpty()) return emptyList()

    val cache = TransactionManager.current().entityCache

    val toLoadMappings = parentMappings.filter { (parentId, _) ->
        cache.getReferrers<R2dbcEntity<Any>>(parentId, refColumn) == null
    }

    if (toLoadMappings.isEmpty()) {
        // Already cached for every parent — return the union of cached children so
        // transitive preloading of remaining relations still has entities to recurse on.
        return parentMappings.flatMap { (parentId, _) ->
            cache.getReferrers<R2dbcEntity<Any>>(parentId, refColumn)?.toList().orEmpty()
        }
    }

    val refereeValuesToLoad = toLoadMappings.map { it.second }.distinct()
    val condition = buildInListCondition(refColumn, refereeValuesToLoad)
    val loadedChildren = factory.find { condition }.toList()

    val grouped: Map<Any, List<R2dbcEntity<Any>>> = loadedChildren.groupBy { child ->
        @Suppress("UNCHECKED_CAST")
        child.lookupRefValue(refColumn as Column<Any?>) as Any
    }

    parentMappings.forEach { (parentId, refereeValue) ->
        cache.getOrPutReferrers(refColumn, parentId) {
            // NOTE: we deliberately use `SizedCollection(emptyList())` rather than `emptySized()`
            // for parents with no children. R2DBC's `EmptySizedIterable.collect` throws
            // UnsupportedOperationException, which would propagate when the cached value is
            // later read via `.toList()` (e.g. by transitive preload or by the user).
            SizedCollection(grouped[refereeValue] ?: emptyList())
        }
    }

    return loadedChildren
}

private suspend fun <ID : Any> List<R2dbcEntity<ID>>.preloadInnerTableLink(
    accessor: R2dbcInnerTableLinkAccessor<ID, R2dbcEntity<ID>, Any, R2dbcEntity<Any>>
): List<R2dbcEntity<*>> {
    val link = accessor.link
    val sourceColumn = link.sourceColumn
    val target = link.target

    val parentIds = mapNotNull { entity -> entity.id._value?.let { entity.id } }
    if (parentIds.isEmpty()) return emptyList()

    val distinctParentIds = parentIds.distinct()
    val cache = TransactionManager.current().entityCache

    val toLoad = distinctParentIds.filter { id ->
        cache.getReferrers<R2dbcEntity<Any>>(id, sourceColumn) == null
    }

    if (toLoad.isEmpty()) {
        return distinctParentIds.flatMap { id ->
            cache.getReferrers<R2dbcEntity<Any>>(id, sourceColumn)?.toList().orEmpty()
        }
    }

    val (columns, entityTables) = link.columnsAndTables

    val rows = entityTables.select(columns).where { sourceColumn inList toLoad }
        .toList()

    val pairs: List<Pair<EntityID<ID>, R2dbcEntity<Any>>> = rows.map { row ->
        @Suppress("UNCHECKED_CAST")
        val parentId = row[sourceColumn] as EntityID<ID>
        val targetEntity = target.wrapRow(row) as R2dbcEntity<Any>
        parentId to targetEntity
    }

    val groupedBySourceId: Map<EntityID<ID>, List<R2dbcEntity<Any>>> = pairs
        .groupBy({ it.first }, { it.second })

    toLoad.forEach { id ->
        cache.getOrPutReferrers(sourceColumn, id) {
            SizedCollection(groupedBySourceId[id] ?: emptyList())
        }
    }

    return pairs.map { it.second }.distinct()
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
