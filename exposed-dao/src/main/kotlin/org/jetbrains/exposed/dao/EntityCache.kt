package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transactionScope
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

val Transaction.entityCache : EntityCache by transactionScope { EntityCache(this) }

@Suppress("UNCHECKED_CAST")
class EntityCache(private val transaction: Transaction) {
    internal var flushingEntities by transactionScope { false }
    val data = LinkedHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()
    val inserts = LinkedHashMap<IdTable<*>, MutableList<Entity<*>>>()
    val referrers = HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>>()

    private fun getMap(f: EntityClass<*, *>) : MutableMap<Any, Entity<*>> = getMap(f.table)

    private fun getMap(table: IdTable<*>) : MutableMap<Any, Entity<*>> = data.getOrPut(table) {
        LinkedHashMap()
    }

    fun <ID: Any, R: Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: ()-> SizedIterable<R>): SizedIterable<R> =
            referrers.getOrPut(sourceId){ HashMap() }.getOrPut(key) { LazySizedCollection(refs()) } as SizedIterable<R>

    fun <ID:Comparable<ID>, T: Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? = getMap(f)[id.value] as T? ?: inserts[f.table]?.firstOrNull { it.id == id } as? T

    fun <ID:Comparable<ID>, T: Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values as Collection<T>

    fun <ID:Comparable<ID>, T: Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f)[o.id.value] = o
    }

    fun store(o: Entity<*>) {
        getMap(o.klass.table)[o.id.value] = o
    }

    fun <ID:Comparable<ID>, T: Entity<ID>> remove(table: IdTable<ID>, o: T) {
        getMap(table).remove(o.id.value)
    }

    fun <ID:Comparable<ID>, T: Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
        inserts.getOrPut(f.table) { arrayListOf() }.add(o as Entity<*>)
    }

    fun flush() {
        flush(inserts.keys + data.keys)
    }

    private fun updateEntities(idTable: IdTable<*>) {
        data[idTable]?.let { map ->
            if (map.isNotEmpty()) {
                val updatedEntities = HashSet<Entity<*>>()
                val batch = EntityBatchUpdate(map.values.first().klass)
                for ((_, entity) in map) {
                    if (entity.flush(batch)) {
                        check(entity.klass !is ImmutableEntityClass<*, *>) { "Update on immutable entity ${entity.javaClass.simpleName} ${entity.id}" }
                        updatedEntities.add(entity)
                    }
                }
                batch.execute(transaction)
                updatedEntities.forEach {
                    transaction.registerChange(it.klass, it.id, EntityChangeType.Updated)
                }
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
                removeTablesReferrers(insertedTables)
            }
        } finally {
            flushingEntities = false
        }
    }

    internal fun removeTablesReferrers(insertedTables: Collection<Table>) {
        referrers.filterValues { it.any { it.key.table in insertedTables } }.map { it.key }.forEach {
            referrers.remove(it)
        }
    }

    internal fun flushInserts(table: IdTable<*>) {
        inserts.remove(table)?.let {
            var toFlush: List<Entity<*>> = it
            do {
                val partition = toFlush.partition {
                    it.writeValues.none {
                        val (key, value) = it
                        key.referee == table.id && value is EntityID<*> && value._value == null
                    }
                }
                toFlush = partition.first
                val ids = table.batchInsert(toFlush) { entry ->
                    for ((c, v) in entry.writeValues) {
                        this[c] = v
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
                }
                toFlush = partition.second
            } while(toFlush.isNotEmpty())
        }
    }

    fun clearReferrersCache() {
        referrers.clear()
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