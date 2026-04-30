package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.LazySizedCollection
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.insert
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

val R2dbcTransaction.entityCache: R2dbcEntityCache by transactionScope {
    R2dbcEntityCache(this as R2dbcTransaction)
}

class R2dbcEntityCache(private val transaction: R2dbcTransaction) {
    val data = ConcurrentHashMap<IdTable<*>, MutableMap<Any, R2dbcEntity<*>>>()

    @Volatile
    private var flushingEntities = false

    internal val inserts = ConcurrentHashMap<IdTable<*>, MutableSet<R2dbcEntity<*>>>()

    internal val updates = ConcurrentHashMap<IdTable<*>, MutableSet<R2dbcEntity<*>>>()

    internal val referrers = ConcurrentHashMap<Column<*>, MutableMap<EntityID<*>, SizedIterable<*>>>()

    fun <ID : Any, T : R2dbcEntity<ID>> find(f: R2dbcEntityClass<ID, T>, id: EntityID<ID>): T? =
        // Mirrors JDBC's `EntityCache.find`. Unlike JDBC we can't dereference `id.value` blindly
        // (it would throw on an un-flushed entity), so the first lookup is gated by `id._value`.
        (id._value?.let { getMap(f)[it] as T? })
            ?: inserts[f.table]?.firstOrNull { it.id == id } as? T
            ?: initializingEntities.firstOrNull { it.klass == f && it.id == id } as? T

    private fun getMap(f: R2dbcEntityClass<*, *>): MutableMap<Any, R2dbcEntity<*>> = getMap(f.table)

    private fun getMap(table: IdTable<*>): MutableMap<Any, R2dbcEntity<*>> = data.getOrPut(table) {
        LimitedHashMap()
    }

    private inner class LimitedHashMap<K, V> : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntitiesToStore
        }
    }

    var maxEntitiesToStore = transaction.db.config.maxEntitiesToStoreInCachePerEntity
        set(value) {
            val diff = value - field
            field = value
            if (diff < 0) {
                data.values.forEach { it.trimToFirst(value) }
            }
        }

    /** Stores the specified [R2dbcEntity] in this cache using its associated [R2dbcEntityClass] as the key. */
    fun <ID : Any, T : R2dbcEntity<ID>> store(f: R2dbcEntityClass<ID, T>, o: T) {
        getMap(f)[o.id.value] = o
    }

    /**
     * Stores the specified [R2dbcEntity] in this cache.
     *
     * The [R2dbcEntityClass] associated with this entity is inferred from its [R2dbcEntity.klass] property.
     */
    fun store(o: R2dbcEntity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    fun <ID : Any, T : R2dbcEntity<ID>> remove(table: IdTable<ID>, o: T) {
        // Mirrors JDBC's `EntityCache.remove`. Guard around `id._value`: in R2DBC an un-flushed
        // entity's `id.value` would throw (no `invokeOnNoValue` flush), so just skip — the entity
        // can't be in `data` yet.
        o.id._value?.let { getMap(table).remove(it) }
    }

    fun <ID : Any> scheduleUpdate(klass: R2dbcEntityClass<ID, R2dbcEntity<ID>>, entity: R2dbcEntity<ID>) {
        updates.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    fun <ID : Any, T : R2dbcEntity<ID>> findAll(entityClass: R2dbcEntityClass<ID, T>): List<T> {
        val map = data[entityClass.table] ?: return emptyList()
        return map.values.toList() as List<T>
    }

    private val initializingEntities = LinkedIdentityHashSet<R2dbcEntity<*>>()

    fun <ID : Any> isEntityInInitializationState(entity: R2dbcEntity<ID>): Boolean {
        return initializingEntities.contains(entity)
    }

    fun <ID : Any> isScheduledForInsert(entity: R2dbcEntity<ID>): Boolean {
        return inserts[entity.klass.table]?.contains(entity) ?: false
    }

    fun <ID : Any> isStoredInData(entity: R2dbcEntity<ID>): Boolean {
        val value = entity.id._value ?: return false
        return data[entity.klass.table]?.get(value) === entity
    }

    fun <ID : Any> addNotInitializedEntityToQueue(entity: R2dbcEntity<ID>) {
        require(initializingEntities.add(entity)) { "Entity ${entity::class.simpleName} already in initialization process" }
    }

    fun <ID : Any> finishEntityInitialization(entity: R2dbcEntity<ID>) {
        require(initializingEntities.lastOrNull() == entity) {
            "Can't finish initialization for entity ${entity::class.simpleName} - the initialization order is broken"
        }
        initializingEntities.remove(entity)
    }

    fun <ID : Any> scheduleInsert(klass: R2dbcEntityClass<ID, R2dbcEntity<ID>>, entity: R2dbcEntity<ID>) {
        inserts.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    // TODO parameters have other order rather tahn in jdbc alternative
    suspend fun <ID : Any, R : R2dbcEntity<ID>> getOrPutReferrers(
        column: Column<*>,
        sourceId: EntityID<*>,
        refs: suspend () -> SizedIterable<@UnsafeVariance R>
    ): SizedIterable<R> {
        val columnReferrers = referrers.getOrPut(column) { ConcurrentHashMap() }
        @Suppress("UNCHECKED_CAST")
        return columnReferrers.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    fun <R : R2dbcEntity<*>> getReferrers(sourceId: EntityID<*>, key: Column<*>): SizedIterable<R>? {
        @Suppress("UNCHECKED_CAST")
        return referrers[key]?.get(sourceId) as? SizedIterable<R>
    }

    fun removeReferrer(column: Column<*>, entityId: EntityID<*>) {
        referrers[column]?.remove(entityId)
    }

    suspend fun clear(flush: Boolean = true) {
        if (flush) flush()
        data.clear()
        inserts.clear()
        updates.clear()
        clearReferrersCache()
    }

    fun clearReferrersCache() {
        referrers.clear()
    }

    suspend fun <ID : Any> updateEntities(table: IdTable<ID>) {
        val entitiesToUpdate = updates.remove(table)?.toList().orEmpty()
        if (entitiesToUpdate.isEmpty()) return

        @Suppress("ForbiddenComment")
        // TODO: Implement proper batch update execution
        for (entity in entitiesToUpdate) {
            entity.flush(null)
        }
    }

    suspend fun flush() {
        val toFlush = when {
            inserts.isEmpty() && updates.isEmpty() -> emptyList()
            inserts.isNotEmpty() && updates.isNotEmpty() -> inserts.keys + updates.keys
            inserts.isNotEmpty() -> inserts.keys
            else -> updates.keys
        }
        flush(toFlush)
    }

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
        } finally {
            flushingEntities = false
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
        val entitiesWithSelfRefs = mutableListOf<R2dbcEntity<*>>()

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

suspend fun R2dbcTransaction.flushCache(): List<R2dbcEntity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}

/**
 * Drops entries from the front of this map until its [size] is at most [maxSize].
 *
 * Extracted from `R2dbcEntityCache.maxEntitiesToStore`'s setter so the setter reads as intent
 * ("trim each per-table map to the new max") rather than carrying the iterator mechanics inline.
 * Relies on insertion-order iteration of the per-table cache (see [R2dbcEntityCache.LimitedHashMap])
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
