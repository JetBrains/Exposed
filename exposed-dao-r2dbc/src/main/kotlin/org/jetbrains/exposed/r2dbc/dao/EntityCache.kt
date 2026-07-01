package org.jetbrains.exposed.r2dbc.dao

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.LazySizedCollection
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/** The current [EntityCache] for [this][R2dbcTransaction] scope, or a new instance if none exists. */
@ExperimentalR2dbcDaoApi
val R2dbcTransaction.entityCache: EntityCache by transactionScope {
    EntityCache(this as R2dbcTransaction)
}

/**
 * Class responsible for the storage of [Entity] instances in a specific [transaction].
 */
@ExperimentalR2dbcDaoApi
class EntityCache(private val transaction: R2dbcTransaction) {
    /** The mapping of [IdTable]s to associated [Entity] instances (as a mapping of entity id values to entities). */
    val data = ConcurrentHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()

    @Volatile
    private var flushingEntities = false

    internal val inserts = ConcurrentHashMap<IdTable<*>, MutableSet<Entity<*>>>()

    internal val updates = ConcurrentHashMap<IdTable<*>, MutableSet<Entity<*>>>()

    internal val referrers = ConcurrentHashMap<Column<*>, MutableMap<EntityID<*>, SizedIterable<*>>>()

    // It's needed to make settning references synchronous
    internal val pendingInnerTableLinkUpdates = mutableListOf<suspend () -> Unit>()

    /**
     * Searches this [EntityCache] for an [Entity] by its [EntityID] value using its associated [EntityClass] as the key.
     *
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     */
    fun <ID : Any, T : Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? =
        // Mirrors JDBC's `EntityCache.find`. Unlike JDBC we can't dereference `id.value` blindly
        // (it would throw on an un-flushed entity), so the first lookup is gated by `id._value`.
        (id._value?.let { getMap(f)[it] as T? })
            ?: inserts[f.table]?.firstOrNull { it.id == id } as? T
            ?: initializingEntities.firstOrNull { it.klass == f && it.id == id } as? T

    private fun getMap(f: EntityClass<*, *>): MutableMap<Any, Entity<*>> = getMap(f.table)

    private fun getMap(table: IdTable<*>): MutableMap<Any, Entity<*>> = data.getOrPut(table) {
        LimitedHashMap()
    }

    private inner class LimitedHashMap<K, V> : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntitiesToStore
        }
    }

    /**
     * The amount of entities to store in this [EntityCache] per [Entity] class.
     *
     * By default, this value is configured by `DatabaseConfig.maxEntitiesToStoreInCachePerEntity`,
     * which defaults to storing all entities.
     *
     * On setting a new value, all data stored in the cache will be adjusted to the new size. If the new value
     * is less than the current cache size by N, the first N entities stored will be removed. If the new value
     * is greater than the current cache size, the adjusted cache will only be filled with more entities after
     * they are retrieved, for example by calling [EntityClass.all].
     */
    var maxEntitiesToStore = transaction.db.config.maxEntitiesToStoreInCachePerEntity
        set(value) {
            val diff = value - field
            field = value
            if (diff < 0) {
                data.values.forEach { it.trimToFirst(value) }
            }
        }

    /** Stores the specified [Entity] in this cache using its associated [EntityClass] as the key. */
    fun <ID : Any, T : Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f)[o.id.value] = o
    }

    /**
     * Stores the specified [Entity] in this cache.
     *
     * The [EntityClass] associated with this entity is inferred from its [Entity.klass] property.
     */
    fun store(o: Entity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    /** Removes the specified [Entity] from this [EntityCache] using its associated [table] as the key. */
    fun <ID : Any, T : Entity<ID>> remove(table: IdTable<ID>, o: T) {
        // Mirrors JDBC's `EntityCache.remove`. Guard around `id._value`: in R2DBC an un-flushed
        // entity's `id.value` would throw (no `invokeOnNoValue` flush), so just skip — the entity
        // can't be in `data` yet.
        o.id._value?.let { getMap(table).remove(it) }
    }

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be updated in the database. */
    fun <ID : Any> scheduleUpdate(klass: EntityClass<ID, Entity<ID>>, entity: Entity<ID>) {
        updates.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    /** Gets all [Entity] instances in this [EntityCache] that match the associated [EntityClass]. */
    fun <ID : Any, T : Entity<ID>> findAll(entityClass: EntityClass<ID, T>): List<T> {
        val map = data[entityClass.table] ?: return emptyList()
        return map.values.toList() as List<T>
    }

    private val initializingEntities = LinkedIdentityHashSet<Entity<*>>()

    internal fun <ID : Any> isEntityInInitializationState(entity: Entity<ID>): Boolean {
        return initializingEntities.contains(entity)
    }

    internal fun <ID : Any> isScheduledForInsert(entity: Entity<ID>): Boolean {
        return inserts[entity.klass.table]?.contains(entity) ?: false
    }

    internal fun <ID : Any> isStoredInData(entity: Entity<ID>): Boolean {
        val value = entity.id._value ?: return false
        return data[entity.klass.table]?.get(value) === entity
    }

    internal fun <ID : Any> addNotInitializedEntityToQueue(entity: Entity<ID>) {
        require(initializingEntities.add(entity)) { "Entity ${entity::class.simpleName} already in initialization process" }
    }

    internal fun <ID : Any> finishEntityInitialization(entity: Entity<ID>) {
        require(initializingEntities.lastOrNull() == entity) {
            "Can't finish initialization for entity ${entity::class.simpleName} - the initialization order is broken"
        }
        initializingEntities.remove(entity)
    }

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be inserted into the database. */
    fun <ID : Any> scheduleInsert(klass: EntityClass<ID, Entity<ID>>, entity: Entity<ID>) {
        inserts.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     *
     * If either the [key] column is not present or a value does not exist for the parent entity, the default [refs]
     * will be called and its result will be put into the map under the given keys and the call result returned.
     */
    suspend fun <ID : Any, R : Entity<ID>> getOrPutReferrers(
        sourceId: EntityID<*>,
        key: Column<*>,
        refs: suspend () -> SizedIterable<@UnsafeVariance R>
    ): SizedIterable<R> {
        val columnReferrers = referrers.getOrPut(key) { ConcurrentHashMap() }
        @Suppress("UNCHECKED_CAST")
        return columnReferrers.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     */
    fun <R : Entity<*>> getReferrers(sourceId: EntityID<*>, key: Column<*>): SizedIterable<R>? {
        @Suppress("UNCHECKED_CAST")
        return referrers[key]?.get(sourceId) as? SizedIterable<R>
    }

    /**
     * Clears this [EntityCache] of all stored data, including any reference mappings.
     *
     * @param flush By default, pending inserts and updates for all cached entities will first be sent to the
     * database. If this is set to `false`, any pending operations will not be flushed and will be removed as well.
     */
    suspend fun clear(flush: Boolean = true) {
        if (flush) flush()
        data.clear()
        inserts.clear()
        updates.clear()
        pendingInnerTableLinkUpdates.clear()
        clearReferrersCache()
    }

    /** Clears this [EntityCache] of stored data that maps cached parent entities to their referencing child entities. */
    fun clearReferrersCache() {
        referrers.clear()
    }

    private suspend fun <ID : Any> updateEntities(table: IdTable<ID>) {
        val update = updates.remove(table) ?: return
        if (update.isEmpty()) return

        val updatedEntities = HashSet<Entity<*>>()
        val batch = EntityBatchUpdate(update.first().klass)

        for (entity in update) {
            if (entity.flush(batch)) {
                updatedEntities.add(entity)
            }
        }

        executeAsPartOfEntityLifecycle {
            batch.execute(transaction)
        }

        updatedEntities.forEach {
            transaction.registerChange(it.klass, it.id, EntityChangeType.Updated)
        }
    }

    /** Sends all pending inserts and updates for all [Entity] instances in this [EntityCache] to the database. */
    suspend fun flush() {
        if (inserts.isEmpty() && updates.isEmpty() && pendingInnerTableLinkUpdates.isEmpty()) return
        val toFlush = when {
            inserts.isNotEmpty() && updates.isNotEmpty() -> inserts.keys + updates.keys
            inserts.isNotEmpty() -> inserts.keys
            updates.isNotEmpty() -> updates.keys
            else -> emptyList()
        }
        flush(toFlush)
    }

    /**
     * Sends all pending inserts and updates for [Entity] instances in this [EntityCache] to the database.
     *
     * The only entities that will be flushed are those that can be associated with any of the specified [tables].
     */
    suspend fun flush(tables: Iterable<IdTable<*>>) {
        if (flushingEntities) return
        try {
            flushingEntities = true
            val insertedTables = inserts.keys

            val updateBeforeInsert = SchemaUtils.sortTablesByReferences(insertedTables).filterIsInstance<IdTable<*>>()
            updateBeforeInsert.forEach { updateEntities(it) }

            SchemaUtils.sortTablesByReferences(tables).filterIsInstance<IdTable<*>>().forEach { flushInserts(it) }

            val updateTheRestTables = tables - updateBeforeInsert.toSet()
            for (t in updateTheRestTables) {
                updateEntities(t)
            }

            if (insertedTables.isNotEmpty()) {
                removeTablesReferrers(insertedTables, true)
            }

            if (pendingInnerTableLinkUpdates.isNotEmpty()) {
                executePendingInnerTableLinkUpdates()
            }
        } finally {
            flushingEntities = false
        }
    }

    private suspend fun executePendingInnerTableLinkUpdates() {
        // Flush all remaining inserts/updates first — the deferred link operations
        // need entities from arbitrary tables to have IDs.
        val remainingInserts = inserts.keys.toList()
        for (table in SchemaUtils.sortTablesByReferences(remainingInserts).filterIsInstance<IdTable<*>>()) {
            flushInserts(table)
        }
        val remainingUpdates = updates.keys.toList()
        for (table in remainingUpdates) {
            updateEntities(table)
        }

        val pending = pendingInnerTableLinkUpdates.toList()
        pendingInnerTableLinkUpdates.clear()
        for (op in pending) {
            op()
        }
    }

    internal fun removeTablesReferrers(tables: Collection<Table>, isInsert: Boolean) {
        val insertedTablesSet = tables.toSet()
        val columnsToInvalidate = tables.flatMapTo(hashSetOf()) { table ->
            table.columns.mapNotNull { column -> column.takeIf { it.referee != null } }
        }

        columnsToInvalidate.forEach {
            referrers.remove(it)
        }

        referrers.keys.filter { refColumn ->
            when {
                isInsert -> false
                refColumn.referee?.table in insertedTablesSet -> true
                refColumn.table.columns.any { it.referee?.table in tables } -> true
                else -> false
            }
        }.forEach {
            referrers.remove(it)
        }
    }

    internal suspend fun <ID : Any> flushInserts(table: IdTable<ID>) {
        val entitiesToInsert = inserts.remove(table)?.toList().orEmpty()
        if (entitiesToInsert.isEmpty()) return

        // We have to handle self references in r2dbc here comparing to jdbc, because
        // in jdbc the entity that gets self reference would be inserted in the moment
        // of setting that self reference
        val entitiesWithSelfRefs = mutableListOf<Entity<*>>()

        for (entity in entitiesToInsert) {
            val entityId = entity.id
            val allWriteValues = entity.writeValues.toMap()

            // Separate self-references to the same table whose target id is not yet generated.
            // Such values cannot be included in the INSERT because the referenced id does not
            // exist yet. They are applied as a follow-up UPDATE once the id has been generated.
            val selfRefs = allWriteValues.filter { (key, value) ->
                key.referee == table.id && value is EntityID<*> && value._value == null
            }
            val insertValues = allWriteValues - selfRefs.keys

            val insertStatement = table.insert {
                for ((column, value) in insertValues) {
                    it[column] = value
                }
            }

            val resultRow = insertStatement.resultedValues?.firstOrNull()

            if (resultRow != null) {
                val generatedId = resultRow[table.id]
                if (entityId._value == null) {
                    entityId._value = generatedId.value
                }
                entity._readValues = resultRow
            }

            // If the INSERT didn't return values for database-generated columns (e.g. DEFAULTs or
            // BEFORE INSERT triggers on dialects that don't support RETURNING for all columns),
            // re-SELECT the row so users can read those values without an explicit refresh().
            val needsRefresh = entityId._value != null && table.columns.any { col ->
                col.isDatabaseGenerated() && entity._readValues?.hasValue(col) != true
            }
            if (needsRefresh) {
                @Suppress("UNCHECKED_CAST")
                val freshRow = table.selectAll().where { table.id eq entityId as EntityID<ID> }.firstOrNull()
                if (freshRow != null) entity._readValues = freshRow
            }

            entity.writeValues.clear()

            // Restore self-reference values to writeValues for a post-insert update.
            // The referenced id is now populated, so these can be safely serialized.
            if (selfRefs.isNotEmpty()) {
                for ((column, value) in selfRefs) {
                    entity.writeValues[column] = value
                }
                entitiesWithSelfRefs.add(entity)
            }

            store(entity)

            transaction.registerChange(entity.klass, entity.id, EntityChangeType.Created)
        }

        for (entity in entitiesWithSelfRefs) {
            entity.flush()
        }

        transaction.alertSubscribers()
    }
}

/**
 * Sends all pending [Entity] inserts and updates stored in this transaction's [EntityCache] to the database.
 *
 * @return A list of all new entities that were stored as scheduled for insert.
 */
@ExperimentalR2dbcDaoApi
suspend fun R2dbcTransaction.flushCache(): List<Entity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}

/**
 * Drops entries from the front of this map until its [size] is at most [maxSize].
 *
 * Extracted from `EntityCache.maxEntitiesToStore`'s setter so the setter reads as intent
 * ("trim each per-table map to the new max") rather than carrying the iterator mechanics inline.
 * Relies on insertion-order iteration of the per-table cache (see [EntityCache.LimitedHashMap])
 * to evict the oldest entries first.
 */
private fun <K, V> MutableMap<K, V>.trimToFirst(maxSize: Int) {
    val sizeExceed = size - maxSize
    if (sizeExceed <= 0) return
    val iterator = iterator()
    repeat(sizeExceed) {
        iterator.next()
        iterator.remove()
    }
}
