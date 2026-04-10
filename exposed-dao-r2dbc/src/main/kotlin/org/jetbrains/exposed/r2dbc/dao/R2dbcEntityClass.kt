package org.jetbrains.exposed.r2dbc.dao

import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.r2dbc.dao.exceptions.R2dbcEntityNotFoundException
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.Query
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

        val row = find { table.id eq id }.firstOrNull()
        return row?.let { wrapRow(it) }
    }

    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    open fun all(): Query {
        warmCache()
        return table.selectAll()
    }

    fun find(op: Op<Boolean>): Query {
        warmCache()
        return searchQuery(op)
    }

    fun find(op: () -> Op<Boolean>): Query = find(op())

    open fun searchQuery(op: Op<Boolean>): Query =
        dependsOnTables.selectAll().where { op }.notForUpdate()

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
