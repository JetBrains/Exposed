package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.ITransaction

@Suppress("UNCHECKED_CAST")
open class EntityCache() : ICache {
	override lateinit var transaction: DaoTransaction
	private val data = LinkedHashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()
	private val inserts = LinkedHashMap<IdTable<*>, MutableList<Entity<*>>>()
	private val referrers = HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>>()

	private fun getMap(f: EntityClass<*, *>): MutableMap<Any, Entity<*>> = getMap(f.table)

	private fun getMap(table: IdTable<*>): MutableMap<Any, Entity<*>> = data.getOrPut(table) {
		LinkedHashMap()
	}

	override fun <ID : Any, R : Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: () -> SizedIterable<R>): SizedIterable<R> =
			referrers.getOrPut(sourceId) { HashMap() }.getOrPut(key) { LazySizedCollection(refs()) } as SizedIterable<R>

	override fun <ID : Comparable<ID>, T : Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? = getMap(f)[id.value] as T? ?: inserts[f.table]?.firstOrNull { it.id == id } as? T

	override fun <ID : Comparable<ID>, T : Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values as Collection<T>

	override fun <ID : Comparable<ID>, T : Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
		getMap(f)[o.id.value] = o
	}

	override fun store(o: Entity<*>) {
		getMap(o.klass.table)[o.id.value] = o
	}

	override fun <ID : Comparable<ID>, T : Entity<ID>> remove(table: IdTable<ID>, o: T) {
		getMap(table).remove(o.id.value)
	}

	override fun <ID : Comparable<ID>, T : Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
		inserts.getOrPut(f.table) { mutableListOf() }.add(o as Entity<*>)
	}

	override fun flush() {
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

	override fun flush(tables: Iterable<IdTable<*>>) {
		if (transaction.flushingEntities) return
		try {
			transaction.flushingEntities = true
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
			transaction.flushingEntities = false
		}
	}

	override fun removeTablesReferrers(insertedTables: Collection<Table>) {
		referrers.filterValues { it.any { it.key.table in insertedTables } }.map { it.key }.forEach {
			referrers.remove(it)
		}
	}

	override fun flushCache(): List<Entity<*>> {
		return getNewEntities()
	}

	override fun flushInserts(table: IdTable<*>) {
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
			} while (toFlush.isNotEmpty())
		}
	}

	override fun clearReferrersCache() {
		referrers.clear()
	}

	override fun getReferrer(entityId: Any): MutableMap<Column<*>, SizedIterable<*>>? {
		return referrers[entityId]
	}

	override fun getReferrers(): HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>> {
		return referrers
	}

	override fun removeReferrer(entityId: EntityID<*>) {
		referrers.remove(entityId)
	}

	override fun clearData() {
		data.clear()
	}

	override fun getInsert(table: IdTable<*>): MutableList<Entity<*>>? {
		return inserts.get(table)
	}

	override fun clearInserts() {
		inserts.clear()
	}

	override fun getNewEntities(): List<Entity<*>> {
		val newEntities = inserts.flatMap { it.value }
		flush()
		return newEntities
	}

	override fun getInserts(): LinkedHashMap<IdTable<*>, MutableList<Entity<*>>> {
		return inserts
	}

	override fun getData(table: IdTable<*>): MutableMap<Any, Entity<*>> {
		return data[table] ?: mutableMapOf()
	}

	override fun setData(table: IdTable<*>, entry: MutableMap<Any, Entity<*>>) {
		data[table] = entry
	}

	companion object {

		fun invalidateGlobalCaches(created: List<Entity<*>>) {
			created.asSequence().mapNotNull { it.klass as? ImmutableCachedEntityClass<*, *> }.distinct().forEach {
				it.expireCache()
			}
		}
	}
}

fun ITransaction.flushCache(): List<Entity<*>> {
    with(this) {
	    this as DaoTransaction
        val newEntities = this.getInserts().flatMap { it.value }
        this.flush()
        return newEntities
    }
}