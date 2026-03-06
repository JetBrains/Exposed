package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.transactions.transactionScope
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
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

    var maxEntitiesToStore = transaction.db.config.maxEntitiesToStoreInCachePerEntity

    fun <ID : Any, T : R2dbcEntity<ID>> find(entityClass: R2dbcEntityClass<ID, T>, id: EntityID<ID>): T? {
        val map = data[entityClass.table] ?: return null
        return map[id.value] as T?
            ?: inserts[entityClass.table]?.firstOrNull { it.id == id } as? T
            ?: initializingEntities.firstOrNull { it.klass == entityClass && it.id == id } as? T
    }

    fun <ID : Any> store(entity: R2dbcEntity<ID>) {
        val map = data.getOrPut(entity.klass.table) { ConcurrentHashMap() }
        map[entity.id.value] = entity
    }

    fun <ID : Any> remove(table: IdTable<ID>, entity: R2dbcEntity<ID>) {
        data[table]?.remove(entity.id.value)
    }

    fun <ID : Any> scheduleUpdate(klass: R2dbcEntityClass<ID, R2dbcEntity<ID>>, entity: R2dbcEntity<ID>) {
        updates.getOrPut(klass.table) { LinkedIdentityHashSet() }.add(entity)
    }

    fun <ID : Any, T : R2dbcEntity<ID>> findAll(entityClass: R2dbcEntityClass<ID, T>): List<T> {
        val map = data[entityClass.table] ?: return emptyList()
        return map.values.toList() as List<T>
    }

    private val initializingEntities = LinkedIdentityHashSet<R2dbcEntity<*>>()

    val pendingInitializationLambdas = ConcurrentHashMap<R2dbcEntity<*>, MutableList<suspend (R2dbcEntity<*>) -> Unit>>()

    fun <ID : Any> isEntityInInitializationState(entity: R2dbcEntity<ID>): Boolean {
        return initializingEntities.contains(entity)
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

    suspend fun <ID : Any> getOrPutReferrers(
        column: Column<*>,
        sourceId: EntityID<*>,
        refs: suspend () -> Any
    ): Any {
        val columnReferrers = referrers.getOrPut(column) { ConcurrentHashMap() }
        return columnReferrers.getOrPut(sourceId) { refs() }
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

        for (entity in entitiesToInsert) {
            val entityId = entity.id
            val writeValues = entity.writeValues.toMap()

            val insertStatement = table.insert {
                for ((column, value) in writeValues) {
                    @Suppress("UNCHECKED_CAST")
                    it[column as Column<Any?>] = value
                }
            }

            val resultRow = insertStatement.resultedValues?.firstOrNull()

            if (resultRow != null) {
                @Suppress("UNCHECKED_CAST")
                val generatedId = resultRow[table.id] as EntityID<ID>
                if (entityId._value == null) {
                    entityId._value = generatedId.value
                }
                entity._readValues = resultRow
            }

            entity.writeValues.clear()
            store(entity)
        }
    }
}
