package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
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
// TODO ALIGN_WITH_JDBC: JDBC has a single `Iterable<T>.with` because its SizedIterable is also
//  an Iterable. R2DBC has to expose two overloads (this one + the Iterable overload below). If
//  R2DBC's SizedIterable ever stops being `Flow`-only, these can collapse into one.
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
 * Eager-loads the specified [relations] for all entities in this in-memory [Iterable] (e.g. a
 * plain `List<Entity>`). Mirrors JDBC's `Iterable.with`. This overload exists because R2DBC's
 * [SizedIterable] is a `Flow`, not an `Iterable`, so the two receivers cannot be unified.
 */
suspend fun <SRCID : Any, SRC : R2dbcEntity<SRCID>, REF : R2dbcEntity<*>, L : Iterable<SRC>> L.with(
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
suspend fun <SRCID : Any, SRC : R2dbcEntity<SRCID>> SRC.load(
    vararg relations: KProperty1<out R2dbcEntity<*>, Any?>
): SRC = apply {
    listOf(this).with(*relations)
}

@Suppress("UNCHECKED_CAST")
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

    // Composite-FK: fall back to a per-child `findById(CompositeID)` (N+1 but populates the
    // entity cache; bulk fetch with `compoundOr` is left for a follow-up).
    accessor.references?.let { refs ->
        return preloadCompositeReference(this, factory, reference as Column<Any?>, refs)
    }

    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }
    if (refIds.isEmpty()) return emptyList()

    val referee = reference.referee ?: return emptyList()
    val condition = buildInListCondition(referee, refIds.distinct())
    val loadedParents = factory.find { condition }.toList()

    // Mirrors JDBC's `storeReferenceCache`: pin the loaded parent on each child's per-entity
    // reference cache so reads work after the transaction ends under
    // `keepLoadedReferencesOutOfTransaction = true`.
    val parentByKey = loadedParents.indexedByRefereeValue(referee)
    forEach { child ->
        val refValue = child.lookupRefValue(reference) ?: return@forEach
        val parent = parentByKey[normalizeRefKey(refValue)] ?: return@forEach
        child.storeReferenceInCache(reference, parent)
    }

    return loadedParents
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

    accessor.references?.let { refs ->
        return preloadCompositeReference(this, factory, reference, refs)
    }

    val refIds = mapNotNull { entity -> entity.lookupRefValue(reference) }

    val referee = reference.referee ?: return emptyList()
    val loadedParents = if (refIds.isEmpty()) {
        emptyList()
    } else {
        val condition = buildInListCondition(referee, refIds.distinct())
        factory.find { condition }.toList()
    }

    val parentByKey = loadedParents.indexedByRefereeValue(referee)
    forEach { child ->
        val refValue = child.lookupRefValue(reference)
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
 * and fetch via [R2dbcEntityClass.findById]. Each fetched parent is stored in the transaction's
 * entity cache (by `findById`) and pinned on the child's per-entity reference cache.
 *
 * TODO ALIGN_WITH_JDBC: JDBC uses `warmUpOptByCompositeReferences` to bulk-fetch with one query
 *  (a compound OR of per-child AND clauses). Until that's ported we issue one query per child.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> preloadCompositeReference(
    children: List<R2dbcEntity<ID>>,
    factory: R2dbcEntityClass<Any, R2dbcEntity<Any>>,
    reference: Column<Any?>,
    references: Map<Column<*>, Column<*>>
): List<R2dbcEntity<*>> {
    val loaded = mutableListOf<R2dbcEntity<*>>()
    children.forEach { child ->
        // Collect raw FK column values up-front so we can short-circuit when any of them is null
        // (CompositeID's builder rejects empty mappings, so we must avoid even constructing it).
        val rawValues = references.map { (childColumn, parentColumn) ->
            val raw = child.writeValues[childColumn as Column<Any?>]
                ?: child._readValues?.getOrNull(childColumn)
            Triple(childColumn, parentColumn, raw)
        }
        if (rawValues.any { it.third == null }) {
            child.storeReferenceInCache(reference, null)
            return@forEach
        }
        val parentIdValue = CompositeID { id ->
            rawValues.forEach { (childColumn, parentColumn, raw) ->
                val pid = parentColumn as Column<EntityID<Any>>
                // Unwrap EntityID when child column stores a raw value.
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

private fun List<R2dbcEntity<*>>.indexedByRefereeValue(referee: Column<*>): Map<Any, R2dbcEntity<*>> {
    val result = HashMap<Any, R2dbcEntity<*>>(size)
    for (parent in this) {
        val raw = parent.lookupRefValue(referee) ?: continue
        result[normalizeRefKey(raw)] = parent
    }
    return result
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
    val allReferences = referrers.allReferences

    // Composite-FK fall-through — fetch children per-parent with a compound AND condition.
    // TODO ALIGN_WITH_JDBC: JDBC's `warmUpReferences` uses `compoundOr` of per-parent ANDs to do
    //  a single bulk query; we issue one query per parent here.
    val isComposite = allReferences.size > 1 || allReferences.values.firstOrNull()?.table is CompositeIdTable
    if (isComposite) {
        return preloadCompositeReferrers(this, factory, refColumn, allReferences, referrers.getOrderByExpressions())
    }

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
    // Honour any `orderBy` configured on the Referrers delegate (e.g. `referrersOnSuspend X orderBy Y`)
    // so the bulk-loaded list matches the order the user would see from a single-parent fetch.

    @Suppress("SpreadOperator")
    val loadedChildren = factory.find { condition }
        .orderBy(*referrers.getOrderByExpressions())
        .toList()

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

    val parentsById: Map<EntityID<*>, R2dbcEntity<ID>> = associateBy { it.id }
    parentMappings.forEach { (parentId, refereeValue) ->
        val parent = parentsById[parentId] ?: return@forEach
        val children = SizedCollection(grouped[refereeValue] ?: emptyList())
        parent.storeReferenceInCache(refColumn, children)
    }

    return loadedChildren
}

/**
 * Composite-FK variant of [preloadReferrers]. For each parent in [parents], builds a per-parent
 * compound AND condition over all child→parent FK pairs, fetches the matching children, and
 * pins them into both the transaction's referrers cache and the parent's per-entity reference
 * cache. Mirrors JDBC's composite branch in `Referrers.getValue` (References.kt:152–157).
 *
 * TODO ALIGN_WITH_JDBC: JDBC's `warmUpReferences` consolidates this into a single bulk query via
 *  `compoundOr` of per-parent ANDs. Until that's ported we issue one query per parent.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any> preloadCompositeReferrers(
    parents: List<R2dbcEntity<ID>>,
    factory: R2dbcEntityClass<Any, R2dbcEntity<Any>>,
    refColumn: Column<*>,
    references: Map<Column<*>, Column<*>>,
    orderBy: Array<Pair<org.jetbrains.exposed.v1.core.Expression<*>, org.jetbrains.exposed.v1.core.SortOrder>>
): List<R2dbcEntity<*>> {
    val cache = TransactionManager.current().entityCache
    val allLoaded = mutableListOf<R2dbcEntity<*>>()

    for (parent in parents) {
        if (parent.id._value == null) continue

        // Build the per-child-column→parent-value map for this parent.
        var anyNull = false
        val childToParentValue: List<Pair<Column<*>, Any?>> = references.map { (childColumn, parentColumn) ->
            val raw = parent.writeValues[parentColumn as Column<Any?>]
                ?: parent._readValues?.getOrNull(parentColumn)
            if (raw == null) anyNull = true
            val value = if (raw is EntityID<*> && childColumn.columnType !is EntityIDColumnType<*>) raw._value else raw
            childColumn to value
        }
        if (anyNull) continue

        // Skip the fetch if the referrers cache slot is already populated for this parent.
        if (cache.getReferrers<R2dbcEntity<Any>>(parent.id, refColumn) != null) continue

        @Suppress("SpreadOperator")
        val children = factory.find {
            childToParentValue.map { (childColumn, value) ->
                (childColumn as Column<Any?>) eq value
            }.reduce { acc, next -> acc and next }
        }.orderBy(*orderBy).toList()

        cache.getOrPutReferrers(refColumn, parent.id) { SizedCollection(children) }
        parent.storeReferenceInCache(refColumn, SizedCollection(children))
        allLoaded += children
    }

    return allLoaded
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

    val parentsById: Map<EntityID<ID>, R2dbcEntity<ID>> = associateBy { it.id }
    distinctParentIds.forEach { id ->
        val parent = parentsById[id] ?: return@forEach
        parent.storeReferenceInCache(sourceColumn, SizedCollection(groupedBySourceId[id] ?: emptyList()))
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
