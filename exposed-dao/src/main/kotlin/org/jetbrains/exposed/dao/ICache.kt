package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.Table

interface ICache {

	// We can't supply "this" as an argument to a delegate (when we construct EntityCache in DaoTransaction)
	public var transaction: DaoTransaction

	fun clearReferrersCache()

	fun flush()

	fun flush(tables: Iterable<IdTable<*>>)

	fun flushCache(): List<Entity<*>>

	fun flushInserts(table: IdTable<*>)

	fun <ID:Comparable<ID>, T: Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T?

	fun <ID:Comparable<ID>, T: Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T>

	fun store(o: Entity<*>)

	fun <ID:Comparable<ID>, T: Entity<ID>> store(f: EntityClass<ID, T>, o: T)

	fun <ID:Comparable<ID>, T: Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T)

	fun <ID:Comparable<ID>, T: Entity<ID>> remove(table: IdTable<ID>, o: T)

	fun <ID: Any, R: Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: ()-> SizedIterable<R>): SizedIterable<R>

	fun removeTablesReferrers(insertedTables: Collection<Table>)

	// Any is effectively the type we used in the static cache when it could access the array directly.
	// Probably need to go through and make this type safe (which might be a lot of work)
	fun getReferrer(entityId: Any): MutableMap<Column<*>, SizedIterable<*>>?

	fun getReferrers(): HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>>

	fun removeReferrer(entityId: EntityID<*>)

	fun getNewEntities(): List<Entity<*>>

	fun clearData()

	fun clearInserts()

	fun getInsert(table: IdTable<*>): MutableList<Entity<*>>?

	fun getInserts(): LinkedHashMap<IdTable<*>, MutableList<Entity<*>>>

	fun getData(table: IdTable<*>): MutableMap<Any, Entity<*>>

	fun setData(table: IdTable<*>, entry: MutableMap<Any, Entity<*>>)

	companion object {

		fun invalidateGlobalCaches(created: List<Entity<*>>) {
			created.asSequence().mapNotNull { it.klass as? ImmutableCachedEntityClass<*, *> }.distinct().forEach {
				it.expireCache()
			}
		}
	}
}