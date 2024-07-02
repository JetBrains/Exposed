package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.*

/** The current [EntityCache] for [this][Transaction] scope, or a new instance if none exists. */
val Transaction.entityCache: EntityCache by transactionScope { EntityCache(this) }

/**
 * Class responsible for the storage of [Entity] instances in a specific [transaction].
 */
@Suppress("UNCHECKED_CAST")
class EntityCache(private val transaction: Transaction) {
    private var flushingEntities = false
    private var initializingEntities: LinkedIdentityHashSet<Entity<*>> = LinkedIdentityHashSet()
    internal val pendingInitializationLambdas = IdentityHashMap<Entity<*>, MutableList<(Entity<*>) -> Unit>>()

    /** The mapping of [IdTable]s to associated [Entity] instances (as a mapping of entity id values to entities). */
    val data = LinkedHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()
    internal val inserts = LinkedHashMap<IdTable<*>, MutableSet<Entity<*>>>()
    private val updates = LinkedHashMap<IdTable<*>, MutableSet<Entity<*>>>()
    internal val referrers = HashMap<Column<*>, MutableMap<EntityID<*>, SizedIterable<*>>>()

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
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityCacheTests.changeEntityCacheMaxEntitiesToStoreInMiddleOfTransaction
     */
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

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.preloadReferrersOnAnEntity
     */
    fun <R : Entity<*>> getReferrers(sourceId: EntityID<*>, key: Column<*>): SizedIterable<R>? {
        return referrers[key]?.get(sourceId) as? SizedIterable<R>
    }

    /**
     * Returns a [SizedIterable] containing all child [Entity] instances that reference the parent entity with
     * the provided [sourceId] using the specified [key] column.
     *
     * If either the [key] column is not present or a value does not exist for the parent entity, the default [refs]
     * will be called and its result will be put into the map under the given keys and the call result returned.
     */
    fun <ID : Any, R : Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: () -> SizedIterable<R>): SizedIterable<R> {
        return referrers.getOrPut(key) { HashMap() }.getOrPut(sourceId) { LazySizedCollection(refs()) } as SizedIterable<R>
    }

    /**
     * Searches this [EntityCache] for an [Entity] by its [EntityID] value using its associated [EntityClass] as the key.
     *
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     */
    fun <ID : Comparable<ID>, T : Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? =
        getMap(f)[id.value] as T?
            ?: inserts[f.table]?.firstOrNull { it.id == id } as? T
            ?: initializingEntities.firstOrNull { it.klass == f && it.id == id } as? T

    /**
     * Gets all [Entity] instances in this [EntityCache] that match the associated [EntityClass].
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityCacheTests.testPerTransactionEntityCacheLimit
     */
    fun <ID : Comparable<ID>, T : Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values as Collection<T>

    /** Stores the specified [Entity] in this [EntityCache] using its associated [EntityClass] as the key. */
    fun <ID : Comparable<ID>, T : Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f)[o.id.value] = o
    }

    /**
     * Stores the specified [Entity] in this [EntityCache].
     *
     * The [EntityClass] associated with this entity will be inferred based on its [Entity.klass] property.
     */
    fun store(o: Entity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    /** Removes the specified [Entity] from this [EntityCache] using its associated [table] as the key. */
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

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be inserted into the database. */
    fun <ID : Comparable<ID>, T : Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
        inserts.getOrPut(f.table) { LinkedIdentityHashSet() }.add(o as Entity<*>)
    }

    /** Stores the specified [Entity] in this [EntityCache] as scheduled to be updated in the database. */
    fun <ID : Comparable<ID>, T : Entity<ID>> scheduleUpdate(f: EntityClass<ID, T>, o: T) {
        updates.getOrPut(f.table) { LinkedIdentityHashSet() }.add(o as Entity<*>)
    }

    /** Sends all pending inserts and updates for all [Entity] instances in this [EntityCache] to the database. */
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
        val update = updates.remove(idTable) ?: return
        if (update.isEmpty()) return

        val updatedEntities = HashSet<Entity<*>>()
        val batch = EntityBatchUpdate(update.first().klass)

        for (entity in update) {
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

    /**
     * Sends all pending inserts and updates for [Entity] instances in this [EntityCache] to the database.
     *
     * The only entities that will be flushed are those that can be associated with any of the specified [tables].
     */
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

    @Suppress("TooGenericExceptionCaught")
    internal fun flushInserts(table: IdTable<*>) {
        var toFlush: List<Entity<*>> = inserts.remove(table)?.toList().orEmpty()
        while (toFlush.isNotEmpty()) {
            val partition = toFlush.partition {
                it.writeValues.none {
                    val (key, value) = it
                    key.referee == table.id && value is EntityID<*> && value._value == null
                }
            }
            toFlush = partition.first
            val ids = try {
                executeAsPartOfEntityLifecycle {
                    table.batchInsert(toFlush) { entry ->
                        for ((c, v) in entry.writeValues) {
                            this[c] = if (c.columnType !is EntityIDColumnType<*> && v is EntityID<*>) {
                                v.value
                            } else {
                                v
                            }
                        }
                    }
                }
            } catch (cause: ArrayIndexOutOfBoundsException) {
                // EXPOSED-191 Flaky Oracle test on TC build
                // this try/catch should help to get information about the flaky test.
                // try/catch can be safely removed after the fixing the issue
                // TooGenericExceptionCaught suppress also can be removed
                val toFlushString = toFlush.joinToString("; ") {
                        entry ->
                    entry.writeValues.map { writeValue -> "${writeValue.key.name}=${writeValue.value}" }.joinToString { ", " }
                }

                exposedLogger.error("ArrayIndexOutOfBoundsException on attempt to make flush inserts. Table: ${table.tableName}, entries: ($toFlushString)", cause)
                throw cause
            }

            for ((entry, genValues) in toFlush.zip(ids)) {
                if (entry.id.isNotInitialized()) {
                    val id = genValues[table.id]
                    entry.id._value = id._value
                    entry.writeIdColumnValue(entry.klass.table, id)
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
        }
        transaction.alertSubscribers()
    }

    /**
     * Clears this [EntityCache] of all stored data, including any reference mappings.
     *
     * @param flush By default, pending inserts and updates for all cached entities will first be sent to the
     * database. If this is set to `false`, any pending operations will not be flushed and will be removed as well.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityCacheTests.changeEntityCacheMaxEntitiesToStoreInMiddleOfTransaction
     */
    fun clear(flush: Boolean = true) {
        if (flush) flush()
        data.clear()
        inserts.clear()
        updates.clear()
        clearReferrersCache()
    }

    /** Clears this [EntityCache] of stored data that maps cached parent entities to their referencing child entities. */
    fun clearReferrersCache() {
        referrers.clear()
    }

    private inner class LimitedHashMap<K, V> : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntitiesToStore
        }
    }

    companion object {
        /**
         * Clears the internal cache of any [created] entity that can be associated
         * with an [ImmutableCachedEntityClass].
         */
        fun invalidateGlobalCaches(created: List<Entity<*>>) {
            created.asSequence().mapNotNull { it.klass as? ImmutableCachedEntityClass<*, *> }.distinct().forEach {
                it.expireCache()
            }
        }
    }
}

/**
 * Sends all pending [Entity] inserts and updates stored in this transaction's [EntityCache] to the database.
 *
 * @return A list of all new entities that were stored as scheduled for insert.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testInsertChildWithFlush
 */
fun Transaction.flushCache(): List<Entity<*>> {
    with(entityCache) {
        val newEntities = inserts.flatMap { it.value }
        flush()
        return newEntities
    }
}
