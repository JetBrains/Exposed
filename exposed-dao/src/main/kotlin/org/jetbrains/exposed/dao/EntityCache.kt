package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

val Transaction.entityCache: EntityCache by transactionScope { EntityCache(this) }

@Suppress("UNCHECKED_CAST")
class EntityCache(private val transaction: Transaction) {
    private var flushingEntities = false
    private var initializingEntities: LinkedIdentityHashSet<Entity<*>> = LinkedIdentityHashSet()
    internal val pendingInitializationLambdas = IdentityHashMap<Entity<*>, MutableList<(Entity<*>)->Unit>>()
    val data = LinkedHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()
    internal val inserts = LinkedHashMap<IdTable<*>, MutableSet<Entity<*>>>()
    private val updates = LinkedHashMap<IdTable<*>, MutableSet<Entity<*>>>()
    internal val referrers = HashMap<Column<*>, MutableMap<EntityID<*>, SizedIterable<*>>>()

    /**
     * Amount of entities to keep in a cache per an Entity class.
     * On setting a new value all data stored in cache will be adjusted to a new size
     */
    @Suppress("MemberVisibilityCanBePrivate")
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

    private fun getMap(f: EntityClass<*, *>): MutableMap<Any, Entity<*>> = getMap(f.table)

    private fun getMap(table: IdTable<*>): MutableMap<Any, Entity<*>> = data.getOrPut(table) {
        LimitedHashMap()
    }

    fun <R : Entity<*>> getReferrers(sourceId: EntityID<*>, key: Column<*>): SizedIterable<R>? {
        return referrers[key]?.get(sourceId) as? SizedIterable<R>
    }

    fun <ID : Any, R : Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: () -> SizedIterable<R>): SizedIterable<R> {
        return referrers.getOrPut(key) { HashMap() }.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    fun <ID : Comparable<ID>, T : Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? =
        getMap(f)[id.value] as T?
            ?: inserts[f.table]?.firstOrNull { it.id == id } as? T
            ?: initializingEntities.firstOrNull { it.klass == f && it.id == id } as? T

    fun <ID : Comparable<ID>, T : Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values as Collection<T>

    fun <ID : Comparable<ID>, T : Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f)[o.id.value] = o
    }

    fun store(o: Entity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    fun <ID : Comparable<ID>, T : Entity<ID>> remove(table: IdTable<ID>, o: T) {
        getMap(table).remove(o.id.value)
    }

    internal fun addNotInitializedEntityToQueue(entity: Entity<*>) {
        require(initializingEntities.add(entity)) { "Entity ${entity::class.simpleName} already in initialization process" }
    }

    internal fun finishEntityInitialization(entity: Entity<*>) {
        require(initializingEntities.lastOrNull() == entity) {
            "Can't finish initialization for entity ${entity::class.simpleName} - the initialization order is broken"
        }
        initializingEntities.remove(entity)
    }

    internal fun isEntityInInitializationState(entity: Entity<*>) = entity in initializingEntities

    fun <ID : Comparable<ID>, T : Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
        inserts.getOrPut(f.table) { LinkedIdentityHashSet() }.add(o as Entity<*>)
    }

    fun <ID : Comparable<ID>, T : Entity<ID>> scheduleUpdate(f: EntityClass<ID, T>, o: T) {
        updates.getOrPut(f.table) { LinkedIdentityHashSet() }.add(o as Entity<*>)
    }

    fun flush() {
        val toFlush = when {
            inserts.isEmpty() && updates.isEmpty() -> emptyList()
            inserts.isNotEmpty() && updates.isNotEmpty() -> inserts.keys + updates.keys
            inserts.isNotEmpty() -> inserts.keys
            else -> updates.keys
        }
        flush(toFlush)
    }

    private fun updateEntities(idTable: IdTable<*>) {
        updates.remove(idTable)?.takeIf { it.isNotEmpty() }?.let {
            val updatedEntities = HashSet<Entity<*>>()
            val batch = EntityBatchUpdate(it.first().klass)
            for (entity in it) {
                if (entity.flush(batch)) {
                    check(entity.klass !is ImmutableEntityClass<*, *>) { "Update on immutable entity ${entity.javaClass.simpleName} ${entity.id}" }
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
    }

    fun flush(tables: Iterable<IdTable<*>>) {
        if (flushingEntities) return
        try {
            flushingEntities = true
            val insertedTables = inserts.keys

            val updateBeforeInsert = SchemaUtils.sortTablesByReferences(insertedTables).filterIsInstance<IdTable<*>>()
            updateBeforeInsert.forEach(::updateEntities)

            SchemaUtils.sortTablesByReferences(tables).filterIsInstance<IdTable<*>>().forEach(::flushInserts)

            val updateTheRestTables = tables - updateBeforeInsert
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
        val columnsToInvalidate = tables.flatMapTo(hashSetOf()) { it.columns.mapNotNull { it.takeIf { it.referee != null } } }

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

    internal fun flushInserts(table: IdTable<*>) {
        inserts.remove(table)?.let {
            var toFlush: List<Entity<*>> = it.toList()
            do {
                val partition = toFlush.partition {
                    it.writeValues.none {
                        val (key, value) = it
                        key.referee == table.id && value is EntityID<*> && value._value == null
                    }
                }
                toFlush = partition.first
                val ids = executeAsPartOfEntityLifecycle {
                    table.batchInsert(toFlush) { entry ->
                        for ((c, v) in entry.writeValues) {
                            this[c] = v
                        }
                    }
                }

                for ((entry, genValues) in toFlush.zip(ids)) {
                    if (entry.id._value == null) {
                        val id = genValues[table.id]
                        entry.id._value = id._value
                        entry.writeValues[entry.klass.table.id as Column<Any?>] = id
                    }
                    genValues.fieldIndex.keys.forEach { key ->
                        entry.writeValues[key as Column<Any?>] = genValues[key]
                    }

                    entry.storeWrittenValues()
                    store(entry)
                    transaction.registerChange(entry.klass, entry.id, EntityChangeType.Created)
                    pendingInitializationLambdas[entry]?.forEach { it(entry) }
                }

                toFlush = partition.second
            } while (toFlush.isNotEmpty())
        }
        transaction.alertSubscribers()
    }

    fun clear(flush: Boolean = true) {
        if (flush) flush()
        data.clear()
        inserts.clear()
        updates.clear()
        clearReferrersCache()
    }

    fun clearReferrersCache() {
        referrers.clear()
    }

    private inner class LimitedHashMap<K, V> : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntitiesToStore
        }
    }

    companion object {

        fun invalidateGlobalCaches(created: List<Entity<*>>) {
            created.asSequence().mapNotNull { it.klass as? ImmutableCachedEntityClass<*, *> }.distinct().forEach {
                it.expireCache()
            }
        }
    }
}

fun Transaction.flushCache(): List<Entity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}
