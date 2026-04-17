package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.Column
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

    internal val referrers = ConcurrentHashMap<Column<*>, MutableMap<EntityID<*>, Any>>()

    fun <ID : Any, T : R2dbcEntity<ID>> find(entityClass: R2dbcEntityClass<ID, T>, id: EntityID<ID>): T? {
        // `id.value` can not be used, because it can't insert the entity in the case it's null
        if (id._value == null) {
            return inserts[entityClass.table]?.firstOrNull { it.id == id } as? T
                ?: initializingEntities.firstOrNull { it.klass == entityClass && it.id == id } as? T
        }

        return getMap(entityClass)[id.value] as T?
    }

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
                data.values.forEach { map ->
                    val sizeExceed = map.size - value
                    if (sizeExceed > 0) {
                        val iterator = map.iterator()
                        repeat(sizeExceed) {
                            iterator.next()
                            iterator.remove()
                        }
                    }
                }
            }
        }

    fun <ID : Any> store(entity: R2dbcEntity<ID>) {
        val map = data.getOrPut(entity.klass.table) { ConcurrentHashMap() }
        map[entity.id.value] = entity
    }

    fun <ID : Any> remove(table: IdTable<ID>, entity: R2dbcEntity<ID>) {
        // Same as in find(), 'entity.id.value' can not be used directly, because
        // because the entity could not be fetched in this moment. Another option is to
        // make 'remove()' suspend
        if (entity.id._value != null) {
            data[table]?.remove(entity.id._value)
        }
        inserts[table]?.remove(entity)
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

    suspend fun <ID : Any, R : R2dbcEntity<ID>> getOrPutReferrers(
        column: Column<*>,
        sourceId: EntityID<*>,
        refs: suspend () -> SizedIterable<@UnsafeVariance R>
    ): SizedIterable<R> {
        val columnReferrers = referrers.getOrPut(column) { ConcurrentHashMap() }
        @Suppress("UNCHECKED_CAST")
        return columnReferrers.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    fun removeReferrer(column: Column<*>, entityId: EntityID<*>) {
        referrers[column]?.remove(entityId)
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
        if (flushingEntities) {
            return
        }

        try {
            flushingEntities = true

            val insertedTables = inserts.keys
            val updateBeforeInsert = SchemaUtils.sortTablesByReferences(insertedTables).filterIsInstance<IdTable<*>>()
            for (table in updateBeforeInsert) {
                updateEntities(table)
            }

            val tablesToInsert = SchemaUtils.sortTablesByReferences(insertedTables).filterIsInstance<IdTable<*>>()
            for (table in tablesToInsert) {
                flushInserts(table)
            }

            val updateTheRestTables = tables.toSet() - updateBeforeInsert.toSet()
            for (t in updateTheRestTables) {
                updateEntities(t)
            }

            if (insertedTables.isNotEmpty()) {
                removeTablesReferrers(insertedTables)
            }
        } finally {
            flushingEntities = false
        }
    }

    fun removeTablesReferrers(tables: Collection<IdTable<*>>) {
        val columnsToRemove = referrers.keys.filter { column ->
            tables.any { table -> column.table == table }
        }
        columnsToRemove.forEach { column ->
            referrers.remove(column)
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
        }

        for (entity in entitiesWithSelfRefs) {
            entity.flush()
        }
    }
}

suspend fun R2dbcTransaction.flushCache(): List<R2dbcEntity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}
