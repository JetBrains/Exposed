package org.jetbrains.exposed.r2dbc.dao

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.exceptions.R2dbcEntityNotFoundException
import org.jetbrains.exposed.r2dbc.dao.relationships.R2dbcBackReference
import org.jetbrains.exposed.r2dbc.dao.relationships.R2dbcOptionalBackReference
import org.jetbrains.exposed.r2dbc.dao.relationships.R2dbcReferrers
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.ColumnWithTransform
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.columnTransformer
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.emptySized
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
    // TODO ALIGN_WITH_JDBC: no JDBC counterpart — JDBC's `Column.setValue` auto-attaches the entity
    //  by querying the DB synchronously. Revisit when/if R2DBC gains an equivalent implicit path.
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

    /**
     * Wraps the specified [ResultRow] data into an [R2dbcEntity] instance.
     *
     * When an entity is already cached, the method performs a **selective merge**: values for
     * columns present in [row] are used to refresh the entity, while columns absent from [row]
     * (e.g. a partial SELECT) retain their previously cached values.
     *
     * Mirrors JDBC's fix for GitHub issue #1527 — without the merge, a cached entity returned by
     * `SELECT FOR UPDATE` would silently keep its stale `_readValues`, causing lost updates in
     * concurrent increment patterns.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun wrapRow(row: ResultRow): T {
        val entity = wrap(row[table.id], row)

        if (entity._readValues == null) {
            entity._readValues = row
            return entity
        }

        val existingKeys = entity.readValues.fieldIndex.keys
        val fetchedKeys = row.fieldIndex.keys
        val columnToValue = (existingKeys + fetchedKeys).toSet().associateWith { column ->
            if (row.hasValue(column)) row[column] else entity._readValues?.get(column)
        }
        entity._readValues = ResultRow.createAndFillValues(unwrapColumnValues(columnToValue))

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

    fun removeFromCache(entity: R2dbcEntity<ID>) {
        val cache = warmCache()
        cache.remove(table, entity)
        // R2DBC's `Entity.delete` skips the round-trip INSERT+DELETE for unflushed entities, so we
        // also need to drop the entity from the scheduled inserts. JDBC doesn't need this because
        // the lifecycle interceptor flushes inserts before the DELETE statement.
        cache.inserts[table]?.remove(entity)
        cache.referrers.forEach { (col, referrers) ->
            // Remove references from entity to other entities
            referrers.remove(entity.id)

            // Remove references from other entities to this entity
            if (col.table == table) {
                with(entity) { col.lookup() }?.let { referrers.remove(it as EntityID<*>) }
            }
        }
    }

    /**
     * One-to-many reference. R2DBC counterpart of JDBC's `referrersOn`.
     */
    // TODO ALIGN_WITH_JDBC: the relationship DSL is suffixed `*Suspend` (referrersOnSuspend,
    //  optionalReferrersOnSuspend, backReferencedOnSuspend, optionalBackReferencedOnSuspend) to
    //  disambiguate from the JDBC names that return entities directly. Once a unified naming
    //  scheme is agreed across modules, drop the suffix here.
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF> R2dbcEntityClass<TargetID, Target>.referrersOnSuspend(
        column: Column<REF>
    ): R2dbcReferrers<ID, R2dbcEntity<ID>, TargetID, Target, REF> =
        R2dbcReferrers(column, this, cache = true)

    /** Two-argument form to control caching behaviour. */
    fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF> R2dbcEntityClass<TargetID, Target>.referrersOnSuspend(
        column: Column<REF>,
        cache: Boolean
    ): R2dbcReferrers<ID, R2dbcEntity<ID>, TargetID, Target, REF> =
        R2dbcReferrers(column, this, cache)

    /**
     * Optional one-to-many reference. R2DBC counterpart of JDBC's `optionalReferrersOn`.
     */
    infix fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF : Any>
        R2dbcEntityClass<TargetID, Target>.optionalReferrersOnSuspend(
            column: Column<REF?>
        ): R2dbcReferrers<ID, R2dbcEntity<ID>, TargetID, Target, REF?> =
        R2dbcReferrers(column, this, cache = true)

    /** Two-argument form to control caching behaviour. */
    fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF : Any>
        R2dbcEntityClass<TargetID, Target>.optionalReferrersOnSuspend(
            column: Column<REF?>,
            cache: Boolean
        ): R2dbcReferrers<ID, R2dbcEntity<ID>, TargetID, Target, REF?> =
        R2dbcReferrers(column, this, cache)

    /**
     * One-to-one back reference. R2DBC counterpart of JDBC's `backReferencedOn`.
     */
    infix fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF>
        R2dbcEntityClass<TargetID, Target>.backReferencedOnSuspend(
            column: Column<REF>
        ): R2dbcBackReference<TargetID, Target, ID, R2dbcEntity<ID>, REF> =
        R2dbcBackReference(column, this)

    /**
     * Optional one-to-one back reference. R2DBC counterpart of JDBC's `optionalBackReferencedOn`.
     */
    infix fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF>
        R2dbcEntityClass<TargetID, Target>.optionalBackReferencedOnSuspend(
            column: Column<REF?>
        ): R2dbcOptionalBackReference<TargetID, Target, ID, R2dbcEntity<ID>, REF> =
        R2dbcOptionalBackReference(column, this)

    /**
     * Overload of [optionalBackReferencedOnSuspend] for non-nullable reference columns.
     * Mirrors JDBC's overloaded `optionalBackReferencedOn(Column<REF>)`.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("optionalBackReferencedOnSuspendNonNullable")
    infix fun <TargetID : Any, Target : R2dbcEntity<TargetID>, REF : Any>
        R2dbcEntityClass<TargetID, Target>.optionalBackReferencedOnSuspend(
            column: Column<REF>
        ): R2dbcOptionalBackReference<TargetID, Target, ID, R2dbcEntity<ID>, REF> =
        R2dbcOptionalBackReference(column as Column<REF?>, this)

    @Suppress("UNCHECKED_CAST")
    suspend fun <SID : Any> warmUpLinkedReferences(
        references: List<EntityID<SID>>,
        linkTable: Table,
        forUpdate: Boolean? = null,
        optimizedLoad: Boolean = false
    ): List<T> {
        if (references.isEmpty()) return emptyList()

        val sourceRefColumn = linkTable.columns
            .singleOrNull { it.referee == references.first().table.id } as? Column<EntityID<SID>>
            ?: error("Can't detect source reference column")
        val targetRefColumn = linkTable.columns
            .singleOrNull { it.referee == table.id } as? Column<EntityID<ID>>
            ?: error("Can't detect target reference column")

        return warmUpLinkedReferences(references, sourceRefColumn, targetRefColumn, linkTable, forUpdate, optimizedLoad)
    }

    @Suppress("UNCHECKED_CAST", "ComplexMethod", "LongMethod")
    suspend fun <SID : Any> warmUpLinkedReferences(
        references: List<EntityID<SID>>,
        sourceRefColumn: Column<EntityID<SID>>,
        targetRefColumn: Column<EntityID<ID>>,
        linkTable: Table,
        forUpdate: Boolean? = null,
        optimizedLoad: Boolean = false
    ): List<T> {
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct()
        val transaction = TransactionManager.current()

        val inCache = transaction.entityCache.referrers[sourceRefColumn]
            ?.filterKeys { distinctRefIds.contains(it) }
            ?: emptyMap()

        val loaded = ((distinctRefIds - inCache.keys).takeIf { it.isNotEmpty() } as List<EntityID<SID>>?)?.let { idsToLoad ->
            val alreadyInJoin = (dependsOnTables as? Join)?.alreadyInJoin(linkTable) ?: false
            val entityTables = if (alreadyInJoin) dependsOnTables else dependsOnTables.join(linkTable, JoinType.INNER, targetRefColumn, table.id)

            val columns = when {
                optimizedLoad -> listOf(sourceRefColumn, targetRefColumn)
                alreadyInJoin -> (dependsOnColumns + sourceRefColumn).distinct()
                else -> (dependsOnColumns + linkTable.columns + sourceRefColumn).distinct()
            }

            val query = entityTables.select(columns).where { sourceRefColumn inList idsToLoad }
            val targetEntities = mutableMapOf<EntityID<ID>, T>()
            val entitiesWithRefs = when (forUpdate) {
                true -> query.forUpdate()
                false -> query.notForUpdate()
                else -> query
            }.map {
                val targetId = it[targetRefColumn]
                if (!optimizedLoad) {
                    targetEntities.getOrPut(targetId) { wrapRow(it) }
                }
                it[sourceRefColumn] to targetId
            }

            if (optimizedLoad) {
                forEntityIds(entitiesWithRefs.map { it.second }.toList()).collect {
                    targetEntities[it.id] = it
                }
            }

            val groupedBySourceId = entitiesWithRefs.toList().groupBy({ it.first }) { targetEntities.getValue(it.second) }

            idsToLoad.forEach {
                transaction.entityCache.getOrPutReferrers(sourceRefColumn, it) {
                    SizedCollection(groupedBySourceId[it] ?: emptyList())
                }
            }
            targetEntities.values
        }

        return inCache.values.flatMap { it.toList() as List<T> } + loaded.orEmpty()
    }

    open fun forEntityIds(ids: List<EntityID<ID>>): SizedIterable<T> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptySized()

        val cached = distinctIds.mapNotNull { testCache(it) }

        if (cached.size == distinctIds.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(table.id inList distinctIds))
    }

    /**
     * Returns an [EntityFieldWithTransform] delegate that transforms this stored [Unwrapped] value on every read.
     *
     * @param transformer An instance of [ColumnTransformer] to handle the transformations.
     */
    fun <Unwrapped, Wrapped> Column<Unwrapped>.transform(
        transformer: ColumnTransformer<Unwrapped, Wrapped>
    ): EntityFieldWithTransform<Unwrapped, Wrapped> = EntityFieldWithTransform(this, transformer, false)

    /**
     * Returns an [EntityFieldWithTransform] delegate that transforms this stored [Unwrapped] value on every read.
     *
     * @param unwrap A pure function that converts a transformed value to a value that can be stored in this original column type.
     * @param wrap A pure function that transforms a value stored in this original column type.
     */
    fun <Unwrapped, Wrapped> Column<Unwrapped>.transform(
        unwrap: (Wrapped) -> Unwrapped,
        wrap: (Unwrapped) -> Wrapped
    ): EntityFieldWithTransform<Unwrapped, Wrapped> = transform(columnTransformer(unwrap, wrap))

    /**
     * Returns an [EntityFieldWithTransform] that extends transformation of an existing [EntityFieldWithTransform].
     *
     * @param unwrap A function that transforms the value to the wrapping type of the previously defined transformation.
     * @param wrap A function that transforms the value to the wrapping type.
     */
    fun <TColumn, Unwrapped, Wrapped> EntityFieldWithTransform<TColumn, Unwrapped>.transform(
        unwrap: (Wrapped) -> Unwrapped,
        wrap: (Unwrapped) -> Wrapped
    ): EntityFieldWithTransform<TColumn, Wrapped> =
        EntityFieldWithTransform(this.column, columnTransformer({ this.unwrap(unwrap(it)) }, { wrap(this.wrap(it)) }), false)

    /**
     * Returns an [EntityFieldWithTransform] delegate that caches the transformed value on first read of
     * this same stored [Unwrapped] value.
     *
     * @param transformer An instance of [ColumnTransformer] to handle the transformations.
     */
    fun <Unwrapped, Wrapped> Column<Unwrapped>.memoizedTransform(
        transformer: ColumnTransformer<Unwrapped, Wrapped>
    ): EntityFieldWithTransform<Unwrapped, Wrapped> = EntityFieldWithTransform(this, transformer, true)

    /**
     * Returns an [EntityFieldWithTransform] delegate that caches the transformed value on first read of
     * this same stored [Unwrapped] value.
     */
    fun <Unwrapped, Wrapped> Column<Unwrapped>.memoizedTransform(
        unwrap: (Wrapped) -> Unwrapped,
        wrap: (Unwrapped) -> Wrapped
    ): EntityFieldWithTransform<Unwrapped, Wrapped> = memoizedTransform(columnTransformer(unwrap, wrap))

    /**
     * Returns an [EntityFieldWithTransform] that extends transformation of an existing [EntityFieldWithTransform]
     * and caches the transformed value on first read.
     */
    fun <TColumn, Unwrapped, Wrapped> EntityFieldWithTransform<TColumn, Unwrapped>.memoizedTransform(
        unwrap: (Wrapped) -> Unwrapped,
        wrap: (Unwrapped) -> Wrapped
    ): EntityFieldWithTransform<TColumn, Wrapped> = EntityFieldWithTransform(
        this.column,
        columnTransformer({ this.unwrap(unwrap(it)) }, { wrap(this.wrap(it)) }),
        true
    )

    suspend fun count(op: Op<Boolean>? = null): Long {
        val countExpression = table.idColumns.first().count()
        val query = table.select(countExpression).notForUpdate()
        op?.let { query.adjustWhere { op } }
        return query.first()[countExpression]
    }

    /**
     * Returns whether the [entityClass] type is equivalent to or a superclass of this [R2dbcEntityClass] instance's [klass].
     * Mirrors JDBC's `EntityClass.isAssignableTo`.
     */
    fun <ID2 : Any, T2 : R2dbcEntity<ID2>> isAssignableTo(entityClass: R2dbcEntityClass<ID2, T2>) =
        entityClass.klass.isAssignableFrom(klass)
}

/**
 * Unwraps any [ColumnWithTransform] values down to the underlying column type. Used by
 * [R2dbcEntityClass.wrapRow]'s selective-merge path so transformed columns aren't re-wrapped
 * when their values are re-stored into [R2dbcEntity._readValues]. Mirrors JDBC's helper.
 */
internal fun <T : Expression<*>> unwrapColumnValues(values: Map<T, Any?>): Map<T, Any?> = values.mapValues { (col, value) ->
    if (col !is ExpressionWithColumnType<*>) return@mapValues value
    value?.let { (col.columnType as? ColumnWithTransform<Any, Any>)?.unwrapRecursive(it) } ?: value
}
