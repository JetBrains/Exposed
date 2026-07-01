package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.getCompositeID
import org.jetbrains.exposed.r2dbc.dao.hasSingleReferenceWithReferee
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
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
 *
 * Note: R2DBC's [SizedIterable] extends `Flow<T>` (not `Iterable<T>` as in JDBC), so we provide
 * a separate [Iterable.with] overload below — they cannot share one generic receiver.
 */
@ExperimentalR2dbcDaoApi
suspend fun <SRCID : Any, SRC : Entity<SRCID>, REF : Entity<*>, L : SizedIterable<SRC>> L.with(
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
 * Eager-loads the specified [relations] for all entities in this in-memory [Iterable] (e.g. a
 * plain `List<Entity>`). Mirrors JDBC's `Iterable.with`. This overload exists because R2DBC's
 * [SizedIterable] is a `Flow`, not an `Iterable`, so the two receivers cannot be unified.
 */
@ExperimentalR2dbcDaoApi
suspend fun <SRCID : Any, SRC : Entity<SRCID>, REF : Entity<*>, L : Iterable<SRC>> L.with(
    vararg relations: KProperty1<out REF, Any?>
): L {
    val asList = toList()
    if (asList.any { it.isNewEntity() }) {
        TransactionManager.current().flushCache()
    }
    asList.preloadRelations(*relations)
    return this
}

/**
 * Eager-loads the specified [relations] for this entity. Mirrors JDBC's `Entity.load`.
 */
@ExperimentalR2dbcDaoApi
suspend fun <SRCID : Any, SRC : Entity<SRCID>> SRC.load(
    vararg relations: KProperty1<out Entity<*>, Any?>
): SRC = apply {
    listOf(this).with(*relations)
}

@Suppress("UNCHECKED_CAST", "NestedBlockDepth")
private suspend fun <ID : Any> List<Entity<ID>>.preloadRelations(
    vararg relations: KProperty1<out Entity<*>, Any?>,
    nodesVisited: MutableSet<EntityClass<*, *>> = mutableSetOf()
) {
    val first = firstOrNull() ?: return
    if (!nodesVisited.add(first.klass)) return

    val directRelations = filterRelationsForEntity(first, relations)
    val loadedByRelation = mutableListOf<Entity<*>>()

    directRelations.forEach { prop ->
        val loaded: List<Entity<*>> = when (val refObject = getReferenceObjectFromDelegatedProperty(first, prop)) {
            is Accessor<*, *, *> ->
                preloadReference(refObject as Accessor<Any, Entity<Any>, Any>)
            is OptionalAccessor<*, *, *> ->
                preloadOptionalReference(refObject as OptionalAccessor<Any, Entity<Any>, Any>)
            is Referrers<*, *, *, *, *> -> {
                (refObject as Referrers<ID, Entity<ID>, Any, Entity<Any>, Any>).let { referrers ->
                    val refColumns = referrers.allReferences
                    val delegateRefColumn = referrers.reference
                    val orderByExpressions = referrers.getOrderByExpressions()
                    val loaded = if (hasSingleReferenceWithReferee(refColumns)) {
                        val castReferee = delegateRefColumn.referee<Any>()!!
                        val refIds = this.map { entity -> entity.getRefereeId(castReferee, delegateRefColumn) }
                        referrers.factory.warmUpReferences(refIds, delegateRefColumn, orderByExpressions)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        referrers.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn, orderBy = orderByExpressions)
                    }
                    storeReferenceCache(delegateRefColumn)
                    loaded
                }
            }
            is InnerTableLinkAccessor<*, *, *, *> ->
                preloadInnerTableLink(refObject as InnerTableLinkAccessor<ID, Entity<ID>, Any, Entity<Any>>)
            is BackReference<*, *, *, *, *> -> {
                (refObject.delegate as Referrers<ID, Entity<ID>, Any, Entity<Any>, Any>).let { referrers ->
                    val refColumns = referrers.allReferences
                    val delegateRefColumn = referrers.reference
                    val orderByExpressions = referrers.getOrderByExpressions()
                    val loaded = if (hasSingleReferenceWithReferee(refColumns)) {
                        val castReferee = delegateRefColumn.referee<Any>()!!
                        val refIds = this.map { entity -> entity.getRefereeId(castReferee, delegateRefColumn) }
                        referrers.factory.warmUpReferences(refIds, delegateRefColumn, orderByExpressions)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        referrers.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn, orderBy = orderByExpressions)
                    }
                    storeReferenceCache(delegateRefColumn)
                    loaded
                }
            }
            is OptionalBackReference<*, *, *, *, *> -> {
                (refObject.delegate as Referrers<ID, Entity<ID>, Any, Entity<Any>, Any>).let { referrers ->
                    val refColumns = referrers.allReferences
                    val delegateRefColumn = referrers.reference
                    val orderByExpressions = referrers.getOrderByExpressions()
                    val loaded = if (hasSingleReferenceWithReferee(refColumns)) {
                        @Suppress("UNCHECKED_CAST")
                        val refIds = this.map { it.resolveColumnValue(delegateRefColumn.referee<Any>()!!) }
                        referrers.factory.warmUpOptReferences(refIds, delegateRefColumn as Column<Any?>, orderByExpressions)
                    } else {
                        val refIds = this.map { it.getCompositeReferrerId(refColumns) }
                        referrers.factory.warmUpCompositeIdReferences(refIds, refColumns, delegateRefColumn, orderBy = orderByExpressions)
                    }
                    storeReferenceCache(delegateRefColumn)
                    loaded
                }
            }
            else -> emptyList()
        }
        loadedByRelation += loaded
    }

    // Mirrors JDBC's recursive step in `preloadRelations`.
    if (directRelations.isNotEmpty() && relations.size != directRelations.size) {
        val remainingRelations = (relations.toList() - directRelations.toSet()).toTypedArray()
        loadedByRelation.groupBy { it::class }.forEach { (_, entities) ->
            (entities as List<Entity<Any>>).preloadRelations(
                relations = remainingRelations,
                nodesVisited = nodesVisited
            )
        }
    }
}

/**
 * Bulk-loads parents referenced by a non-nullable [Accessor]-backed property, for every
 * entity in the receiver list. Loaded parents are inserted into the entity cache by the factory's
 * `find(...)` traversal (via `wrapRow`).
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> List<Entity<ID>>.preloadReference(
    accessor: Accessor<Any, Entity<Any>, Any>
): List<Entity<*>> {
    val reference = accessor.reference
    val factory = accessor.factory

    accessor.references?.let { refs ->
        return preloadCompositeReference(this, factory, reference as Column<Any?>, refs)
    }

    val refIds = mapNotNull { entity -> entity.resolveColumnValue(reference) }
    if (refIds.isEmpty()) return emptyList()

    val referee = reference.referee ?: return emptyList()
    val condition = buildInListCondition(referee, refIds.distinct())
    val loadedParents = factory.find { condition }.toList()

    val parentByKey = loadedParents.indexedByRefereeValue(referee)
    forEach { child ->
        val refValue = child.resolveColumnValue(reference) ?: return@forEach
        val parent = parentByKey[normalizeRefKey(refValue)] ?: return@forEach
        child.storeReferenceInCache(reference, parent)
    }

    return loadedParents
}

/**
 * Bulk-loads parents referenced by an [OptionalAccessor]-backed property.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> List<Entity<ID>>.preloadOptionalReference(
    accessor: OptionalAccessor<Any, Entity<Any>, Any>
): List<Entity<*>> {
    val reference = accessor.reference as Column<Any?>
    val factory = accessor.factory

    accessor.references?.let { refs ->
        return preloadCompositeReference(this, factory, reference, refs)
    }

    val refIds = mapNotNull { entity -> entity.resolveColumnValue(reference) }

    val referee = reference.referee ?: return emptyList()
    val loadedParents = if (refIds.isEmpty()) {
        emptyList()
    } else {
        val condition = buildInListCondition(referee, refIds.distinct())
        factory.find { condition }.toList()
    }

    val parentByKey = loadedParents.indexedByRefereeValue(referee)
    forEach { child ->
        val refValue = child.resolveColumnValue(reference)
        if (refValue == null) {
            child.storeReferenceInCache(reference, null)
        } else {
            parentByKey[normalizeRefKey(refValue)]?.let { parent ->
                child.storeReferenceInCache(reference, parent)
            }
        }
    }

    return loadedParents
}

/**
 * Composite-FK preload: iterate each child, build the composite parent id from its FK columns,
 * and fetch via [EntityClass.findById]. Each fetched parent is stored in the transaction's
 * entity cache (by `findById`) and pinned on the child's per-entity reference cache.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> preloadCompositeReference(
    children: List<Entity<ID>>,
    factory: EntityClass<Any, Entity<Any>>,
    reference: Column<Any?>,
    references: Map<Column<*>, Column<*>>
): List<Entity<*>> {
    val loaded = mutableListOf<Entity<*>>()
    children.forEach { child ->
        val rawValues = references.map { (childColumn, parentColumn) ->
            val raw = child.resolveColumnValue(childColumn)
            Triple(childColumn, parentColumn, raw)
        }
        if (rawValues.any { it.third == null }) {
            child.storeReferenceInCache(reference, null)
            return@forEach
        }
        val parentIdValue = CompositeID { id ->
            rawValues.forEach { (childColumn, parentColumn, raw) ->
                val pid = parentColumn as Column<EntityID<Any>>
                id[pid] = if (raw is EntityID<*> && childColumn.columnType !is EntityIDColumnType<*>) raw._value!! else raw!!
            }
        }
        val parent = factory.findById(parentIdValue as Any) ?: return@forEach
        child.storeReferenceInCache(reference, parent)
        loaded += parent
    }
    return loaded
}

private fun normalizeRefKey(value: Any): Any = (value as? EntityID<*>)?.value ?: value

private fun List<Entity<*>>.indexedByRefereeValue(referee: Column<*>): Map<Any, Entity<*>> {
    val result = HashMap<Any, Entity<*>>(size)
    for (parent in this) {
        val raw = parent.resolveColumnValue(referee) ?: continue
        result[normalizeRefKey(raw)] = parent
    }
    return result
}

@Suppress("UNCHECKED_CAST")
private fun buildInListCondition(referee: Column<*>, refIds: List<Any>): org.jetbrains.exposed.v1.core.Op<Boolean> {
    val baseColumn = referee.takeUnless {
        it.columnType is EntityIDColumnType<*> && refIds.first() !is EntityID<*>
    } ?: (referee.columnType as EntityIDColumnType<Any>).idColumn
    return (baseColumn as Column<Any>) inList refIds
}

private fun Entity<*>.getRefereeId(refereeColumn: Column<*>, delegateRefColumn: Column<*>): Any {
    val refereeValue = resolveColumnValue(refereeColumn)
        ?: error("Referee column ${refereeColumn.name} has no value for entity $id")
    return refereeValue.takeUnless {
        delegateRefColumn.columnType !is EntityIDColumnType<*> && it is EntityID<*>
    } ?: (refereeValue as EntityID<*>).value
}

private fun Entity<*>.getCompositeReferrerId(refColumns: Map<Column<*>, Column<*>>): CompositeID = getCompositeID {
    @Suppress("UNCHECKED_CAST")
    refColumns.map { (child, parent) -> child to (resolveColumnValue(parent) as EntityID<*>).value }
}

/**
 * Mirrors JDBC's `storeReferenceCache(reference, prop)`. In JDBC the property delegate's
 * `getValue` returns the loaded referrers directly, so `prop.get(entity)` works. In R2DBC the
 * delegates return accessor wrappers, so we read from [EntityCache.referrers] instead.
 */
private fun <ID : Any> List<Entity<ID>>.storeReferenceCache(reference: Column<*>) {
    val cache = TransactionManager.current().entityCache
    forEach { entity ->
        val cached = cache.getReferrers<Entity<*>>(entity.id, reference)
        if (cached != null) {
            entity.storeReferenceInCache(reference, cached)
        }
    }
}

private suspend fun <ID : Any> List<Entity<ID>>.preloadInnerTableLink(
    accessor: InnerTableLinkAccessor<ID, Entity<ID>, Any, Entity<Any>>
): List<Entity<*>> {
    val link = accessor.link
    val sourceColumn = link.sourceColumn
    val target = link.target

    val parentIds = mapNotNull { entity -> entity.id._value?.let { entity.id } }
    if (parentIds.isEmpty()) return emptyList()

    val distinctParentIds = parentIds.distinct()
    val cache = TransactionManager.current().entityCache

    val toLoad = distinctParentIds.filter { id ->
        cache.getReferrers<Entity<Any>>(id, sourceColumn) == null
    }

    if (toLoad.isEmpty()) {
        return distinctParentIds.flatMap { id ->
            cache.getReferrers<Entity<Any>>(id, sourceColumn)?.toList().orEmpty()
        }
    }

    val (columns, entityTables) = link.columnsAndTables

    val rows = entityTables.select(columns).where { sourceColumn inList toLoad }
        .toList()

    val pairs: List<Pair<EntityID<ID>, Entity<Any>>> = rows.map { row ->
        @Suppress("UNCHECKED_CAST")
        val parentId = row[sourceColumn] as EntityID<ID>
        val targetEntity = target.wrapRow(row) as Entity<Any>
        parentId to targetEntity
    }

    val groupedBySourceId: Map<EntityID<ID>, List<Entity<Any>>> = pairs
        .groupBy({ it.first }, { it.second })

    toLoad.forEach { id ->
        cache.getOrPutReferrers(id, sourceColumn) {
            SizedCollection(groupedBySourceId[id] ?: emptyList())
        }
    }

    val parentsById: Map<EntityID<ID>, Entity<ID>> = associateBy { it.id }
    distinctParentIds.forEach { id ->
        val parent = parentsById[id] ?: return@forEach
        parent.storeReferenceInCache(sourceColumn, SizedCollection(groupedBySourceId[id] ?: emptyList()))
    }

    return pairs.map { it.second }.distinct()
}

private fun <SRC : Entity<*>> filterRelationsForEntity(
    entity: SRC,
    relations: Array<out KProperty1<out Entity<*>, Any?>>
): Collection<KProperty1<SRC, Any?>> {
    val validMembers = entity::class.memberProperties
    @Suppress("UNCHECKED_CAST")
    return validMembers.filter { it in relations } as Collection<KProperty1<SRC, Any?>>
}

private fun <SRC : Entity<*>> getReferenceObjectFromDelegatedProperty(
    entity: SRC,
    property: KProperty1<SRC, Any?>
): Any? {
    property.isAccessible = true
    return property.getDelegate(entity)
}
