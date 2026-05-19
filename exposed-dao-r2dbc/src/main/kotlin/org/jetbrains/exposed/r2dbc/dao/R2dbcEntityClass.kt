package org.jetbrains.exposed.r2dbc.dao

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.exceptions.R2dbcEntityNotFoundException
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.mapLazy
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

abstract class R2dbcEntityClass<ID : Any, out T : R2dbcEntity<ID>>(
    val table: IdTable<ID>,
    entityType: Class<T>? = null,
    entityCtor: ((EntityID<ID>) -> T)? = null,
) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>

    private val entityPrimaryCtor: KFunction<T> by lazy { klass.kotlin.primaryConstructor as KFunction<T> }

    private val entityCtor: (EntityID<ID>) -> T = entityCtor ?: { entityID -> entityPrimaryCtor.call(entityID) }

    open fun new(init: T.() -> Unit) = new(null, init)

    open val dependsOnTables: ColumnSet get() = table

    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    open fun new(id: ID?, init: T.() -> Unit): T {
        val entityId = if (id == null && table.id.defaultValueFun != null) {
            table.id.defaultValueFun!!()
        } else {
            R2dbcDaoEntityID(id, table)
        }

        val entityCache = warmCache()
        val prototype: T = createInstance(entityId, null)
        prototype.klass = this
        prototype.db = TransactionManager.current().db
        prototype._readValues = ResultRow.createAndFillDefaults(dependsOnColumns)
        if (entityId._value != null) {
            prototype.writeIdColumnValue(table, entityId)
        }
        try {
            entityCache.addNotInitializedEntityToQueue(prototype)
            prototype.init()
        } finally {
            entityCache.finishEntityInitialization(prototype)
        }
        val readValues = prototype._readValues!!
        val writeValues = prototype.writeValues
        table.columns.filter { col ->
            col.defaultValueFun != null && col !in writeValues && readValues.hasValue(col)
        }.forEach { col ->
            @Suppress("UNCHECKED_CAST")
            writeValues[col as Column<Any?>] = readValues[col]
        }
        @Suppress("UNCHECKED_CAST")
        entityCache.scheduleInsert(this as R2dbcEntityClass<ID, R2dbcEntity<ID>>, prototype as R2dbcEntity<ID>)
        return prototype
    }

    protected open fun warmCache(): R2dbcEntityCache = TransactionManager.current().entityCache

    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?): T = entityCtor(entityId)

    open suspend fun findById(id: EntityID<ID>): T? {
        val cached = testCache(id)
        if (cached != null) return cached

        return find { table.id eq id }.firstOrNull()
    }

    suspend fun findByIdAndUpdate(id: ID, block: (it: T) -> Unit): T? {
        val result = find(table.id eq R2dbcDaoEntityID(id, table)).forUpdate().firstOrNull() ?: return null
        block(result)
        return result
    }

    suspend fun findSingleByAndUpdate(op: Op<Boolean>, block: (it: T) -> Unit): T? {
        val result = find(op).forUpdate().singleOrNull() ?: return null
        block(result)
        return result
    }

    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    suspend fun reload(entity: R2dbcEntity<ID>, flush: Boolean = false): T? {
        if (flush) {
            if (entity.isNewEntity()) {
                TransactionManager.current().entityCache.flushInserts(table)
            } else {
                entity.flush()
            }
        }
        removeFromCache(entity)
        return if (entity.id._value != null) findById(entity.id) else null
    }

    // It actually doesn't do 'invalidation', because it's suspend operation, but this method
    // is used on setting value to the entity
    internal open fun invalidateEntityInCache(o: R2dbcEntity<ID>) {
        val sameDatabase = TransactionManager.current().db == o.db
        if (!sameDatabase) return

        val cache = warmCache()

        // TODO could I reverse this condition in the way to check which state of entity should
        //  throw error, rather than which should not.
        if (cache.isEntityInInitializationState(o)) return
        if (cache.isScheduledForInsert(o)) return
        if (cache.isStoredInData(o)) return

        // Not in any tracked state. Either the entity has been deleted in the current
        // transaction, or it was loaded in a different transaction and has not been
        // `attach`-ed here. Both are user bugs — fail loudly.
        //
        // R2DBC cannot mirror JDBC's `get(o.id)` "verify-and-adopt" shortcut because
        // Column.setValue is not a suspend operator and cannot query the database.
        throw R2dbcEntityNotFoundException(o.id, this)
    }

    /**
     * This method is used in r2dbc now for the case when one entity is
     * reused between transactions. In jdbc it's not needed, because there on 'setValue'
     * we could attach it implicitly, but in r2dbc 'setValue' on entity is non-suspendable,
     * so we can't do that, and user must do that explicitly.
     *
     * I still don't like reusing entities between transactions as a pattern, probably it should
     * be deprecated, but it sounds like a bad idea in terms of API changes (even on major versions),
     * because it could be used by many users.
     */
    suspend fun attach(entity: R2dbcEntity<ID>) {
        val cache = warmCache()
        if (cache.find(this, entity.id) != null) return

        // Verify the row still exists — also stores a fresh instance in the cache as a side effect.
        findById(entity.id) ?: throw R2dbcEntityNotFoundException(entity.id, this)

        // Overwrite the freshly-loaded instance with the caller's reference so that subsequent
        // writes on `entity` flow through the same cache entry (mirrors JDBC's `warmCache().store(o)`).
        cache.store(entity)
    }

    open fun all(): SizedIterable<T> = wrapRows(table.selectAll().notForUpdate())

    fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    fun find(op: () -> Op<Boolean>): SizedIterable<T> = find(op())

    open fun searchQuery(op: Op<Boolean>): Query =
        dependsOnTables.select(dependsOnColumns).where { op }.notForUpdate()

    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun wrapRow(row: ResultRow): T {
        val entity = wrap(row[table.id], row)
        if (entity._readValues == null) {
            entity._readValues = row
        }
        return entity
    }

    fun wrap(id: EntityID<ID>, row: ResultRow?): T {
        val transaction = TransactionManager.current()
        return transaction.entityCache.find(this, id) ?: createInstance(id, row).also { new ->
            new.klass = this
            new.db = transaction.db
            warmCache().store(new)
        }
    }

    suspend operator fun get(id: EntityID<ID>): T = findById(id) ?: throw R2dbcEntityNotFoundException(id, this)

    suspend operator fun get(id: ID): T = get(R2dbcDaoEntityID(id, table))

    @Suppress("ForbiddenComment")
    fun removeFromCache(entity: R2dbcEntity<ID>) {
        val cache = warmCache()
        cache.remove(table, entity)
        cache.referrers.forEach { (_, referrers) ->
            referrers.remove(entity.id)

            // TODO Remove references from other entities to this entity
        }
    }
}
