package org.jetbrains.exposed.r2dbc.dao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.r2dbc.dao.relationships.BackReference
import org.jetbrains.exposed.r2dbc.dao.relationships.OptionalBackReference
import org.jetbrains.exposed.r2dbc.dao.relationships.OptionalReference
import org.jetbrains.exposed.r2dbc.dao.relationships.Reference
import org.jetbrains.exposed.r2dbc.dao.relationships.Referrers
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

/**
 * Base class responsible for the management of [Entity] instances and the maintenance of their relation
 * to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities managed by this class.
 * @param [entityType] The expected [Entity] class type. This can be left `null` if it is the class of type
 * argument [T] provided to this [EntityClass] instance.
 * @param [entityCtor] The function invoked to instantiate an [Entity] using a provided [EntityID] value. If a
 * reference to a specific entity constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access (which can be slower).
 */
@Suppress("TooManyFunctions")
@ExperimentalR2dbcDaoApi
abstract class EntityClass<ID : Any, out T : Entity<ID>>(
    val table: IdTable<ID>,
    entityType: Class<T>? = null,
    entityCtor: ((EntityID<ID>) -> T)? = null,
) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>

    private val entityPrimaryCtor: KFunction<T> by lazy { klass.kotlin.primaryConstructor as KFunction<T> }

    private val entityCtor: (EntityID<ID>) -> T = entityCtor ?: { entityID -> entityPrimaryCtor.call(entityID) }

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block. The id will be automatically set.
     *
     * The returned [NewEntity] wraps the prototype until [NewEntity.flush] is invoked, at which point the
     * auto-generated id becomes available. This wrapping is R2DBC-specific because the suspending insert
     * cannot run synchronously inside the non-suspending `new` builder.
     *
     * @param init The block where the entity's fields can be set.
     * @return A [NewEntity] wrapping the entity that has been created.
     */
    open fun new(init: T.() -> Unit) = new(null, init)

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block, schedules an insert, and
     * immediately flushes the cache so the returned entity has its auto-generated id and database-generated columns
     * populated. R2DBC-specific convenience that combines [new] and [NewEntity.flush] in one suspending call.
     *
     * @param init The suspending block where the entity's fields can be set.
     * @return The created and flushed entity.
     */
    open suspend fun newAndFlush(init: suspend T.() -> Unit): T = newAndFlush(null, init)

    /** The [IdTable] that this [EntityClass] depends on when maintaining relations with managed [Entity] instances. */
    open val dependsOnTables: ColumnSet get() = table

    /** The columns that this [EntityClass] depends on when maintaining relations with managed [Entity] instances. */
    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block and with the provided [id].
     *
     * The returned [NewEntity] wraps the prototype until [NewEntity.flush] is invoked, at which point the
     * auto-generated id becomes available.
     *
     * @param id The id of the entity. Set this to `null` if it should be automatically generated.
     * @param init The block where the entity's fields can be set.
     * @return A [NewEntity] wrapping the entity that has been created.
     */
    open fun new(id: ID?, init: T.() -> Unit): NewEntity<ID, T> {
        val entityId = if (id == null && table.id.defaultValueFun != null) {
            table.id.defaultValueFun!!()
        } else {
            DaoEntityID(id, table)
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
        entityCache.scheduleInsert(this as EntityClass<ID, Entity<ID>>, prototype as Entity<ID>)
        return NewEntity(prototype)
    }

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block and with the provided [id],
     * then immediately flushes the cache so the returned entity has its auto-generated id and database-generated
     * columns populated. R2DBC-specific convenience that combines [new] and [NewEntity.flush] in one suspending call.
     *
     * @param id The id of the entity. Set this to `null` if it should be automatically generated.
     * @param init The suspending block where the entity's fields can be set.
     * @return The created and flushed entity.
     */
    open suspend fun newAndFlush(id: ID?, init: suspend T.() -> Unit): T {
        val entityId = if (id == null && table.id.defaultValueFun != null) {
            table.id.defaultValueFun!!()
        } else {
            DaoEntityID(id, table)
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
        entityCache.scheduleInsert(this as EntityClass<ID, Entity<ID>>, prototype as Entity<ID>)
        entityCache.flush()
        return prototype
    }

    /**
     * Instantiates an [EntityCache] with the current [R2dbcTransaction] if one does not already exist in the
     * current transaction scope.
     */
    protected open fun warmCache(): EntityCache = TransactionManager.current().entityCache

    /** Creates a new [Entity] instance with the provided [entityId] value. */
    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?): T = entityCtor(entityId)

    /**
     * Gets an [Entity] by its raw [id] value. Mirrors JDBC's `EntityClass.findById(ID)` —
     * wraps the value in an [EntityID] (or [CompositeID]-backed [DaoEntityID]) before
     * delegating to the [EntityID][findById] overload below.
     */
    suspend fun findById(id: ID): T? = findById(DaoEntityID(id, table))

    /**
     * Gets an [Entity] by its [EntityID] value.
     *
     * @param id The [EntityID] value of the entity.
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     */
    open suspend fun findById(id: EntityID<ID>): T? {
        val cached = testCache(id)
        if (cached != null) return cached

        return find { table.id eq id }.firstOrNull()
    }

    /**
     * Gets an [Entity] by its [id] value and updates the retrieved entity.
     *
     * @param id The id value of the entity.
     * @param block Lambda that contains entity field updates.
     * @return The updated entity that has this id value, or `null` if no entity was found.
     */
    suspend fun findByIdAndUpdate(id: ID, block: (it: T) -> Unit): T? {
        val result = find(table.id eq DaoEntityID(id, table)).forUpdate().firstOrNull() ?: return null
        block(result)
        return result
    }

    /**
     * Gets a single [Entity] that conforms to the [op] conditional expression and updates the retrieved entity.
     *
     * @param op The conditional expression to use when selecting the entity.
     * @param block Lambda that contains entity field updates.
     * @return The updated entity that conforms to this condition, or `null` if either no entity was found
     * or if more than one entity conforms to the condition.
     */
    suspend fun findSingleByAndUpdate(op: Op<Boolean>, block: (it: T) -> Unit): T? {
        val result = find(op).forUpdate().singleOrNull() ?: return null
        block(result)
        return result
    }

    /**
     * Searches the current [EntityCache] for an [Entity] by its [EntityID] value.
     *
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     */
    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    /**
     * Reloads the fields of an [entity] from the database and returns the [entity] as a new object.
     *
     * The original [entity] will also be removed from the current cache.
     * @see removeFromCache
     *
     * @param flush Whether pending entity changes should be flushed prior to reloading.
     */
    suspend fun reload(entity: Entity<ID>, flush: Boolean = false): T? {
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
    internal open fun invalidateEntityInCache(o: Entity<ID>) {
        val sameDatabase = TransactionManager.current().db == o.db
        if (!sameDatabase) return

        val cache = warmCache()

        if (cache.isEntityInInitializationState(o)) return
        if (cache.isScheduledForInsert(o)) return
        if (cache.isStoredInData(o)) return

        // Not in any tracked state. Either the entity was deleted in this transaction,
        // or it was loaded in a different transaction and has not been `attach`-ed here.
        //
        // R2DBC cannot mirror JDBC's `get(o.id)` "verify-and-adopt" shortcut because
        // Column.setValue is not a suspend operator and cannot query the database.
        throw EntityNotFoundException(o.id, this)
    }

    /**
     * R2DBC-specific helper. Registers an [entity] from a previous transaction in the current transaction's cache,
     * allowing it to be read and modified in the new transaction.
     *
     * In JDBC DAO, `Column.setValue` can synchronously query the database and implicitly adopt
     * an entity from another transaction. R2DBC's `setValue` is non-suspend, so this must be
     * done explicitly before mutating the entity.
     *
     * **Behavior:**
     * - Verifies that the row still exists in the database (throws [EntityNotFoundException] if not).
     * - Stores the caller's entity instance in the current transaction's cache, so subsequent
     *   property reads/writes go through this cache entry.
     * - If the entity is already present in the current cache (e.g. already attached or loaded
     *   in this transaction), does nothing.
     *
     * **Flush behavior:**
     * After attaching and modifying an entity, there is no need to call `flush()` explicitly —
     * pending `writeValues` are auto-flushed by `EntityLifecycleInterceptor.beforeCommit`
     * as part of the transaction's commit. This is consistent with JDBC behavior.
     *
     * **Typical usage:**
     * ```kotlin
     * val entity = suspendTransaction { MyEntity.new { name = "foo" }.flush() }
     * suspendTransaction {
     *     MyEntity.attach(entity)   // register in new transaction's cache
     *     entity.name = "bar"       // now safe to modify
     * }
     * ```
     *
     * @throws EntityNotFoundException if the row no longer exists in the database.
     */
    suspend fun attach(entity: Entity<ID>) {
        val cache = warmCache()
        if (cache.find(this, entity.id) != null) return

        // Verify the row still exists — also stores a fresh instance in the cache as a side effect.
        findById(entity.id) ?: throw EntityNotFoundException(entity.id, this)

        // Overwrite the freshly-loaded instance with the caller's reference so that subsequent
        // writes on `entity` flow through the same cache entry (mirrors JDBC's `warmCache().store(o)`).
        cache.store(entity)
    }

    /** Gets all the [Entity] instances associated with this [EntityClass]. */
    open fun all(): SizedIterable<T> = wrapRows(table.selectAll().notForUpdate())

    /**
     * Gets all the [Entity] instances that conform to the [op] conditional expression.
     *
     * @param op The conditional expression to use when selecting the entity.
     * @return A [SizedIterable] of all the entities that conform to this condition.
     */
    fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    /**
     * Gets all the [Entity] instances that conform to the [op] conditional expression.
     *
     * @param op The conditional expression to use when selecting the entity.
     * @return A [SizedIterable] of all the entities that conform to this condition.
     */
    fun find(op: () -> Op<Boolean>): SizedIterable<T> = find(op())

    /**
     * Returns a [Query] to select all columns in [dependsOnTables] with a WHERE clause that includes
     * the provided [op] conditional expression.
     */
    open fun searchQuery(op: Op<Boolean>): Query =
        dependsOnTables.select(dependsOnColumns).where { op }.notForUpdate()

    /**
     * Returns a [SizedIterable] containing entities generated using data retrieved from a database result set in [rows].
     */
    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    /**
     * Wraps the specified [ResultRow] data into an [Entity] instance.
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

    /**
     * Wraps the specified [ResultRow] data into an [Entity] instance.
     *
     * The provided [alias] will be used to adjust the [ResultRow] mapping before returning the entity.
     */
    fun wrapRow(row: ResultRow, alias: Alias<IdTable<*>>): T {
        require(alias.delegate == table) { "Alias for a wrong table ${alias.delegate.tableName} while ${table.tableName} expected" }
        val newFieldsMapping = row.fieldIndex.mapNotNull { (exp, _) ->
            val column = exp as? Column<*>
            val value = row[exp]
            val originalColumn = column?.let { alias.originalColumn(it) }
            when {
                originalColumn != null -> originalColumn to value
                column?.table == alias.delegate -> null
                else -> exp to value
            }
        }.toMap()

        return wrapRow(ResultRow.createAndFillValues(unwrapColumnValues(newFieldsMapping)))
    }

    /**
     * Wraps the specified [ResultRow] data into an [Entity] instance.
     *
     * The provided [alias] will be used to adjust the [ResultRow] mapping before returning the entity.
     */
    fun wrapRow(row: ResultRow, alias: QueryAlias): T {
        require(alias.columns.any { (it.table as Alias<*>).delegate == table }) { "QueryAlias doesn't have any column from ${table.tableName} table" }
        val originalColumns = alias.query.set.source.columns
        val newFieldsMapping = row.fieldIndex.mapNotNull { (exp, _) ->
            val value = row[exp]
            when (exp) {
                is Column if exp.table is Alias<*> -> {
                    val delegate = (exp.table as Alias<*>).delegate
                    val column = originalColumns.single {
                        delegate == it.table && exp.name == it.name
                    }
                    column to value
                }
                is Column if exp.table == table -> null
                else -> exp to value
            }
        }.toMap()

        return wrapRow(ResultRow.createAndFillValues(unwrapColumnValues(newFieldsMapping)))
    }

    /**
     * Returns an [Entity] with the provided [EntityID] value, or, if an entity was not found in the current
     * [EntityCache], creates a new instance using the data in [row].
     */
    fun wrap(id: EntityID<ID>, row: ResultRow?): T {
        val transaction = TransactionManager.current()
        return transaction.entityCache.find(this, id) ?: createInstance(id, row).also { new ->
            new.klass = this
            new.db = transaction.db
            warmCache().store(new)
        }
    }

    /**
     * Gets an [Entity] by its [EntityID] value.
     *
     * @throws EntityNotFoundException if no entity was found.
     */
    suspend operator fun get(id: EntityID<ID>): T = findById(id) ?: throw EntityNotFoundException(id, this)

    /**
     * Gets an [Entity] by its raw [id] value.
     *
     * @throws EntityNotFoundException if no entity was found.
     */
    suspend operator fun get(id: ID): T = get(DaoEntityID(id, table))

    /**
     * Removes the specified [entity] from the current [EntityCache], as well as any stored references to
     * or from the removed entity.
     */
    fun removeFromCache(entity: Entity<ID>) {
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
     * Registers a reference as a field of the child entity class, which returns a parent object of this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * R2DBC counterpart of JDBC's `referencedOn`. Returns a [Reference] that delegates to a suspending
     * [org.jetbrains.exposed.r2dbc.dao.relationships.Accessor] — JDBC's version returns the entity directly via a synchronous lookup.
     */
    infix fun <REF : Any> referencedOn(column: Column<REF>): Reference<ID, @UnsafeVariance T, REF> =
        Reference(column, this)

    /**
     * Composite-FK form of [referencedOn]. R2DBC counterpart of JDBC's `referencedOn(IdTable<*>)`.
     *
     * Resolves the composite foreign-key constraint on [table] that points at this entity's primary key
     * and binds the reference's first FK column as the delegate.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun referencedOn(table: IdTable<*>): Reference<ID, @UnsafeVariance T, Any> {
        val tableFK = getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any>
        return Reference(delegate, this, references = tableFK.references)
    }

    /**
     * Registers an optional reference as a field of the child entity class, which returns a parent object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * R2DBC counterpart of JDBC's `optionalReferencedOn`.
     */
    infix fun <REF : Any> optionalReferencedOn(column: Column<REF?>): OptionalReference<ID, @UnsafeVariance T, REF> =
        OptionalReference(column, this)

    /**
     * Composite-FK form of [optionalReferencedOn]. R2DBC counterpart of JDBC's
     * `optionalReferencedOn(IdTable<*>)`.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun optionalReferencedOn(table: IdTable<*>): OptionalReference<ID, @UnsafeVariance T, Any> {
        val tableFK = getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any?>
        return OptionalReference(delegate, this, references = tableFK.references)
    }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * By default, this also stores the loaded entities to a cache.
     */
    infix fun <TargetID : Any, Target : Entity<TargetID>, REF : Any>
        EntityClass<TargetID, Target>.optionalReferrersOn(
            column: Column<REF?>
        ): Referrers<ID, Entity<ID>, TargetID, Target, REF?> =
        Referrers(column, this, cache = true)

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * Set [cache] to `true` to also store the loaded entities to a cache.
     */
    fun <TargetID : Any, Target : Entity<TargetID>, REF : Any>
        EntityClass<TargetID, Target>.optionalReferrersOn(
            column: Column<REF?>,
            cache: Boolean
        ): Referrers<ID, Entity<ID>, TargetID, Target, REF?> =
        Referrers(column, this, cache)

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     */
    infix fun <TargetID : Any, Target : Entity<TargetID>, REF>
        EntityClass<TargetID, Target>.backReferencedOn(
            column: Column<REF>
        ): BackReference<TargetID, Target, ID, Entity<ID>, REF> =
        BackReference(column, this)

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass` or `null` if no child references the parent entity.
     *
     * The reference could have been defined on the child table in 1 of the following ways:
     * - By the creation of a [column] using either `optReference()` or `reference().nullable()`
     * - By the creation of a non-nullable `reference()` [column] where either 0 or 1 row(s) is expected in the relationship
     */
    infix fun <TargetID : Any, Target : Entity<TargetID>, REF>
        EntityClass<TargetID, Target>.optionalBackReferencedOn(
            column: Column<REF?>
        ): OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF> =
        OptionalBackReference(column, this)

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass` or `null` if no child references the parent entity.
     *
     * Overload of [optionalBackReferencedOn] for non-nullable reference columns — mirrors JDBC's
     * overloaded `optionalBackReferencedOn(Column<REF>)`.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("optionalBackReferencedOnNonNullable")
    infix fun <TargetID : Any, Target : Entity<TargetID>, REF : Any>
        EntityClass<TargetID, Target>.optionalBackReferencedOn(
            column: Column<REF>
        ): OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF> =
        OptionalBackReference(column as Column<REF?>, this)

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * By default, this also stores the loaded entities to a cache.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : Entity<TargetID>, REF> EntityClass<TargetID, Target>.referrersOn(
        column: Column<REF>
    ): Referrers<ID, Entity<ID>, TargetID, Target, REF> =
        Referrers(column, this, cache = true)

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * Set [cache] to `true` to also store the loaded entities to a cache.
     */
    fun <TargetID : Any, Target : Entity<TargetID>, REF>
        EntityClass<TargetID, Target>.referrersOn(
            column: Column<REF>,
            cache: Boolean
        ): Referrers<ID, Entity<ID>, TargetID, Target, REF> =
        Referrers(column, this, cache)

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a foreign key constraint on the child table,
     * by using `foreignKey()`.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : Entity<TargetID>>
        EntityClass<TargetID, Target>.referrersOn(
            table: IdTable<*>
        ): Referrers<ID, Entity<ID>, TargetID, Target, Any> {
        val tableFK = this@EntityClass.getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any>
        return Referrers(delegate, this, cache = true, references = tableFK.references)
    }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a foreign key constraint on the child table,
     * by using `foreignKey()`.
     *
     * By default, this also stores the loaded entities to a cache.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : Entity<TargetID>>
        EntityClass<TargetID, Target>.optionalReferrersOn(
            table: IdTable<*>
        ): Referrers<ID, Entity<ID>, TargetID, Target, Any?> {
        val tableFK = this@EntityClass.getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any?>
        return Referrers(delegate, this, cache = true, references = tableFK.references)
    }

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a foreign key constraint on the child table,
     * by using `foreignKey()`.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : Entity<TargetID>>
        EntityClass<TargetID, Target>.backReferencedOn(
            table: IdTable<*>
        ): BackReference<TargetID, Target, ID, Entity<ID>, Any> {
        val tableFK = this@EntityClass.getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any>
        return BackReference(delegate, this, references = tableFK.references)
    }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass` or `null` if no child references the parent entity.
     *
     * The reference should have been defined by the creation of a foreign key constraint on the child table,
     * by using `foreignKey()`.
     */
    @Suppress("UNCHECKED_CAST")
    infix fun <TargetID : Any, Target : Entity<TargetID>>
        EntityClass<TargetID, Target>.optionalBackReferencedOn(
            table: IdTable<*>
        ): OptionalBackReference<TargetID, Target, ID, Entity<ID>, Any> {
        val tableFK = this@EntityClass.getCompositeForeignKey(table)
        val delegate = tableFK.from.first() as Column<Any?>
        return OptionalBackReference(delegate, this, references = tableFK.references)
    }

    /**
     * Returns the child table's [ForeignKeyConstraint] that matches the primary key columns defined on the table
     * associated with this [EntityClass]. Mirrors JDBC's `EntityClass.getCompositeForeignKey`.
     */
    internal fun getCompositeForeignKey(table: IdTable<*>): ForeignKeyConstraint =
        table.foreignKeys.firstOrNull { it.target == this.table.idColumns }
            ?: error(
                "Table ${table.tableName} does not hold a composite FK constraint matching ${this.table.tableName}'s primary key."
            )

    /**
     * Returns a list of retrieved [Entity] instances whose reference column matches any of the [EntityID] values
     * in [references]. Both the entity's source and target reference columns should have been defined in [linkTable].
     *
     * The [EntityCache] in the current transaction scope will be searched for matching entities.
     *
     * Set [forUpdate] to `true` or `false` depending on whether a locking read should be placed or removed from the
     * search query used. Leave the argument as `null` to use the query without any locking option.
     *
     * Set [optimizedLoad] to `true` to force two queries separately, one for loading ids and another for loading
     * referenced entities. This could be useful when references target the same entities. This will prevent them from
     * loading multiple times (per each reference row) and will require less memory/bandwidth for "heavy" entities
     * (with a lot of columns and/or columns that store large data sizes).
     */
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
    internal suspend fun <SID : Any> warmUpLinkedReferences(
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
                transaction.entityCache.getOrPutReferrers(it, sourceRefColumn) {
                    SizedCollection(groupedBySourceId[it] ?: emptyList())
                }
            }
            targetEntities.values
        }

        return inCache.values.flatMap { it.toList() as List<T> } + loaded.orEmpty()
    }

    /** Returns a [SizedIterable] containing all entities with [EntityID] values from the provided [ids] list. */
    open fun forEntityIds(ids: List<EntityID<ID>>): SizedIterable<T> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptySized()

        val cached = distinctIds.mapNotNull { testCache(it) }

        if (cached.size == distinctIds.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(table.id inList distinctIds))
    }

    /** Returns a [SizedIterable] containing all entities with id values from the provided [ids] list. */
    fun forIds(ids: List<ID>): SizedIterable<T> = forEntityIds(ids.map { DaoEntityID(it, table) })

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

    /**
     * Counts the amount of [Entity] instances that conform to the [op] conditional expression.
     *
     * @param op The conditional expression to use when selecting the entity.
     * @return The amount of entities that conform to this condition.
     */
    suspend fun count(op: Op<Boolean>? = null): Long {
        val countExpression = table.idColumns.first().count()
        val query = table.select(countExpression).notForUpdate()
        op?.let { query.adjustWhere { op } }
        return query.first()[countExpression]
    }

    /**
     * Returns a list of retrieved [Entity] instances whose [refColumn] matches any of the id values in [references].
     *
     * The [EntityCache] in the current transaction scope will be searched for matching entities, if appropriate
     * for [refColumn]'s column type; otherwise, matching results will be queried from the database.
     *
     * Set [orderBy] to specify the order in which entities should be sorted.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <SID> warmUpReferences(
        references: List<SID>,
        refColumn: Column<SID>,
        orderBy: Array<Pair<Expression<*>, SortOrder>>? = null
    ): List<T> {
        val parentTable = refColumn.referee?.table as? IdTable<*>
        requireNotNull(parentTable) { "RefColumn should have reference to IdTable" }
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct()
        val transaction = TransactionManager.current()
        val cache = transaction.entityCache
        val keepLoadedReferenceOutOfTransaction = transaction.db.config.keepLoadedReferencesOutOfTransaction
        if (refColumn.columnType is EntityIDColumnType<*>) {
            refColumn as Column<EntityID<*>>
            distinctRefIds as List<EntityID<ID>>
            val toLoad: List<EntityID<ID>> = distinctRefIds.filter {
                cache.referrers[refColumn]?.containsKey(it)?.not() ?: true
            }
            if (toLoad.isNotEmpty()) {
                val entities = find { refColumn inList toLoad }
                    .orderBy(order = orderBy ?: emptyArray())
                    .toList()

                val result = entities.groupByReference(refColumn = refColumn)

                distinctRefIds.forEach { id ->
                    cache.getOrPutReferrers(id, refColumn) {
                        result[id]?.let { SizedCollection(it) } ?: SizedCollection(emptyList())
                    }.also {
                        if (keepLoadedReferenceOutOfTransaction) {
                            cache.find(this, id as EntityID<ID>)?.storeReferenceInCache(refColumn, it)
                        }
                    }
                }
            }

            return distinctRefIds.flatMap { cache.getReferrers<T>(it, refColumn)?.toList().orEmpty() }
        } else {
            val baseQuery = searchQuery(refColumn inList distinctRefIds)
            val finalQuery = if (parentTable.id in baseQuery.set.fields) {
                baseQuery
            } else {
                baseQuery.adjustSelect { select(fields + parentTable.id) }
                    .adjustColumnSet { innerJoin(parentTable, { refColumn }, { refColumn.referee!! }) }
            }
                .orderBy(order = orderBy ?: emptyArray())

            val entities = wrapRows(finalQuery).toList().distinct()

            entities.groupByReference(refColumn = refColumn).forEach { (id, values) ->
                val castReferee = refColumn.referee
                    .takeUnless { it?.columnType is EntityIDColumnType<*> && id !is EntityID<*> }
                    ?: (refColumn.referee?.columnType as EntityIDColumnType<*>).idColumn
                val parentEntityId: EntityID<*> = parentTable.selectAll().where { castReferee as Column<SID> eq id }
                    .first()[parentTable.id]

                cache.getOrPutReferrers(parentEntityId, refColumn) { SizedCollection(values) }.also {
                    if (keepLoadedReferenceOutOfTransaction) {
                        val childEntity = find { refColumn eq id }.firstOrNull()
                        childEntity?.storeReferenceInCache(refColumn, it)
                    }
                }
            }
            return entities
        }
    }

    /**
     * Returns a list of retrieved [Entity] instances whose [refColumn] optionally matches any of the id values in [references].
     *
     * The [EntityCache] in the current transaction scope will be searched for matching entities, if appropriate
     * for [refColumn]'s column type; otherwise, matching results will be queried from the database.
     *
     * Set [orderBy] to specify the order in which entities should be sorted.
     */
    suspend fun <SID> warmUpOptReferences(
        references: List<SID>,
        refColumn: Column<SID?>,
        orderBy: Array<Pair<Expression<*>, SortOrder>>? = null
    ): List<T> {
        @Suppress("UNCHECKED_CAST")
        return warmUpReferences(references, refColumn as Column<SID>, orderBy)
    }

    @Suppress("UNCHECKED_CAST")
    internal suspend fun warmUpCompositeIdReferences(
        references: List<CompositeID>,
        refColumns: Map<Column<*>, Column<*>>,
        delegateRefColumn: Column<*>,
        orderBy: Array<Pair<Expression<*>, SortOrder>>? = null
    ): List<T> {
        val parentTable = refColumns.values.firstOrNull()?.table as? CompositeIdTable
        requireNotNull(parentTable) { "RefColumns should have reference to CompositeIdTable" }
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct().map { EntityID(it, parentTable) }
        val transaction = TransactionManager.current()
        val cache = transaction.entityCache
        val keepLoadedReferenceOutOfTransaction = transaction.db.config.keepLoadedReferencesOutOfTransaction
        if (refColumns.keys.all { it.columnType is EntityIDColumnType<*> }) {
            val toLoad = distinctRefIds.filter {
                cache.referrers[delegateRefColumn]?.containsKey(it)?.not() ?: true
            }
            if (toLoad.isNotEmpty()) {
                val entities = find { refColumns.keys.toList() inList toLoad.map { it.value } }
                    .orderBy(order = orderBy ?: emptyArray())
                    .toList()
                val result = entities.groupByReference<CompositeID>(refColumns = refColumns)

                distinctRefIds.forEach { id ->
                    cache.getOrPutReferrers(id, delegateRefColumn) {
                        result[id.value]?.let { SizedCollection(it) } ?: SizedCollection(emptyList())
                    }.also {
                        if (keepLoadedReferenceOutOfTransaction) {
                            cache.find(this, id as EntityID<ID>)?.storeReferenceInCache(delegateRefColumn, it)
                        }
                    }
                }
            }

            return distinctRefIds.flatMap { cache.getReferrers<T>(it, delegateRefColumn)?.toList().orEmpty() }
        } else {
            val baseQuery = searchQuery(refColumns.keys.toList() inList distinctRefIds.map { it.value })
                .orderBy(order = orderBy ?: emptyArray())
            val entities = wrapRows(baseQuery).toList().distinct()
            val result = entities.groupByReference<CompositeID>(refColumns = refColumns)

            result.forEach { (id, values) ->
                val parentEntityId: EntityID<*> = parentTable.selectAll().where { parentTable.id eq id }
                    .first()[parentTable.id]

                cache.getOrPutReferrers(parentEntityId, delegateRefColumn) { SizedCollection(values) }.also {
                    if (keepLoadedReferenceOutOfTransaction) {
                        val childEntity = find { refColumns.keys.toList() inList listOf(id) }.firstOrNull()
                        childEntity?.storeReferenceInCache(delegateRefColumn, it)
                    }
                }
            }
            return entities
        }
    }

    private fun <R> List<T>.groupByReference(refColumn: Column<R>): Map<R, List<T>> =
        groupBy { it.readValues[refColumn] }

    @Suppress("UNCHECKED_CAST")
    private fun <R> List<T>.groupByReference(refColumns: Map<Column<*>, Column<*>>): Map<R, List<T>> =
        groupBy { entity ->
            getCompositeID {
                refColumns.map { (child, parent) -> parent to entity.readValues[child] }
            } as R
        }

    /**
     * Returns whether the [entityClass] type is equivalent to or a superclass of this [EntityClass] instance's [klass].
     * Mirrors JDBC's `EntityClass.isAssignableTo`.
     */
    fun <ID2 : Any, T2 : Entity<ID2>> isAssignableTo(entityClass: EntityClass<ID2, T2>) =
        entityClass.klass.isAssignableFrom(klass)
}

internal fun hasSingleReferenceWithReferee(allReferences: Map<Column<*>, Column<*>>?): Boolean {
    return allReferences?.size == 1 && allReferences.values.first().table !is CompositeIdTable
}

@Suppress("UNCHECKED_CAST")
internal fun getCompositeID(entries: () -> List<Pair<Column<*>, *>>): CompositeID = CompositeID {
    entries().forEach { (key, value) ->
        it[key as Column<EntityID<Any>>] = value as Any
    }
}

/**
 * Unwraps any [ColumnWithTransform] values down to the underlying column type. Used by
 * [EntityClass.wrapRow]'s selective-merge path so transformed columns aren't re-wrapped
 * when their values are re-stored into [Entity._readValues]. Mirrors JDBC's helper.
 */
internal fun <T : Expression<*>> unwrapColumnValues(values: Map<T, Any?>): Map<T, Any?> = values.mapValues { (col, value) ->
    if (col !is ExpressionWithColumnType<*>) return@mapValues value
    value?.let { (col.columnType as? ColumnWithTransform<Any, Any>)?.unwrapRecursive(it) } ?: value
}
