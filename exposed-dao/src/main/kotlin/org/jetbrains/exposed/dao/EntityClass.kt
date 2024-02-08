package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor
import kotlin.sequences.Sequence
import kotlin.sequences.any
import kotlin.sequences.filter

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
@Suppress("UNCHECKED_CAST", "UnnecessaryAbstractClass", "TooManyFunctions")
abstract class EntityClass<ID : Comparable<ID>, out T : Entity<ID>>(
    val table: IdTable<ID>,
    entityType: Class<T>? = null,
    entityCtor: ((EntityID<ID>) -> T)? = null,
) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>

    private val entityPrimaryCtor: KFunction<T> by lazy { klass.kotlin.primaryConstructor as KFunction<T> }

    private val entityCtor: (EntityID<ID>) -> T = entityCtor ?: { entityID -> entityPrimaryCtor.call(entityID) }

    operator fun get(id: EntityID<ID>): T = findById(id) ?: throw EntityNotFoundException(id, this)

    operator fun get(id: ID): T = get(DaoEntityID(id, table))

    /**
     * Instantiates an [EntityCache] with the current [Transaction] if one does not already exist in the
     * current transaction scope.
     */
    protected open fun warmCache(): EntityCache = TransactionManager.current().entityCache

    /**
     * Gets an [Entity] by its [id] value.
     *
     * @param id The id value of the entity.
     * @return The entity that has this id value, or `null` if no entity was found.
     */
    fun findById(id: ID): T? = findById(DaoEntityID(id, table))

    /**
     * Gets an [Entity] by its [id] value and updates the retrieved entity.
     *
     * @param id The id value of the entity.
     * @param block Lambda that contains entity field updates.
     * @return The updated entity that has this id value, or `null` if no entity was found.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testDaoFindByIdAndUpdate
     */
    fun findByIdAndUpdate(id: ID, block: (it: T) -> Unit): T? {
        val result = find(table.id eq id).forUpdate().singleOrNull() ?: return null
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
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testDaoFindSingleByAndUpdate
     */
    fun findSingleByAndUpdate(op: Op<Boolean>, block: (it: T) -> Unit): T? {
        val result = find(op).forUpdate().singleOrNull() ?: return null
        block(result)
        return result
    }

    /**
     * Gets an [Entity] by its [EntityID] value.
     *
     * @param id The [EntityID] value of the entity.
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityCacheNotUpdatedOnCommitIssue1380.testRegression
     */
    open fun findById(id: EntityID<ID>): T? = testCache(id) ?: find { table.id eq id }.firstOrNull()

    /**
     * Reloads the fields of an [entity] from the database and returns the [entity] as a new object.
     *
     * The original [entity] will also be removed from the current cache.
     * @see removeFromCache
     *
     * @param flush Whether pending entity changes should be flushed prior to reloading.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testThatUpdateOfInsertedEntitiesGoesBeforeAnInsert
     */
    fun reload(entity: Entity<ID>, flush: Boolean = false): T? {
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

    internal open fun invalidateEntityInCache(o: Entity<ID>) {
        val entityAlreadyFlushed = o.id._value != null
        val sameDatabase = TransactionManager.current().db == o.db
        if (!entityAlreadyFlushed || !sameDatabase) return

        val currentEntityInCache = testCache(o.id)
        if (currentEntityInCache == null) {
            get(o.id) // Check that entity is still exists in database
            warmCache().store(o)
        } else if (currentEntityInCache !== o) {
            exposedLogger.error(
                "Entity instance in cache differs from the provided: ${o::class.simpleName} with ID ${o.id.value}. " +
                    "Changes on entity could be missed."
            )
        }
    }

    /**
     * Searches the current [EntityCache] for an [Entity] by its [EntityID] value.
     *
     * @return The entity that has this wrapped id value, or `null` if no entity was found.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testCacheInvalidatedOnDSLDelete
     */
    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    /**
     * Searches the current [EntityCache] for all [Entity] instances that match the provided [cacheCheckCondition].
     *
     * @return A sequence of matching entities found.
     */
    fun testCache(cacheCheckCondition: T.() -> Boolean): Sequence<T> = warmCache().findAll(this).asSequence().filter { it.cacheCheckCondition() }

    /**
     * Removes the specified [entity] from the current [EntityCache], as well as any stored references to
     * or from the removed entity.
     */
    fun removeFromCache(entity: Entity<ID>) {
        val cache = warmCache()
        cache.remove(table, entity)
        cache.referrers.forEach { (col, referrers) ->
            // Remove references from entity to other entities
            referrers.remove(entity.id)

            // Remove references from other entities to this entity
            if (col.table == table) {
                with(entity) { col.lookup() }?.let { referrers.remove(it as EntityID<*>) }
            }
        }
    }

    /** Returns a [SizedIterable] containing all entities with [EntityID] values from the provided [ids] list. */
    open fun forEntityIds(ids: List<EntityID<ID>>): SizedIterable<T> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptySized()

        val cached = distinctIds.mapNotNull { testCache(it) }

        if (cached.size == distinctIds.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(Op.build { table.id inList distinctIds }))
    }

    /** Returns a [SizedIterable] containing all entities with id values from the provided [ids] list. */
    fun forIds(ids: List<ID>): SizedIterable<T> = forEntityIds(ids.map { DaoEntityID(it, table) })

    /**
     * Returns a [SizedIterable] containing entities generated using data retrieved from a database result set in [rows].
     */
    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    /**
     * Returns a [SizedIterable] containing entities generated using data retrieved from a database result set in [rows].
     *
     * An [alias] should be provided to adjust each [ResultRow] mapping, if necessary, before generating entities.
     */
    fun wrapRows(rows: SizedIterable<ResultRow>, alias: Alias<IdTable<*>>) = rows mapLazy {
        wrapRow(it, alias)
    }

    /**
     * Returns a [SizedIterable] containing entities generated using data retrieved from a database result set in [rows].
     *
     * An [alias] should be provided to adjust each [ResultRow] mapping, if necessary, before generating entities.
     */
    fun wrapRows(rows: SizedIterable<ResultRow>, alias: QueryAlias) = rows mapLazy {
        wrapRow(it, alias)
    }

    /** Wraps the specified [ResultRow] data into an [Entity] instance. */
    @Suppress("MemberVisibilityCanBePrivate")
    fun wrapRow(row: ResultRow): T {
        val entity = wrap(row[table.id], row)
        if (entity._readValues == null) {
            entity._readValues = row
        }

        return entity
    }

    /**
     * Wraps the specified [ResultRow] data into an [Entity] instance.
     *
     * The provided [alias] will be used to adjust the [ResultRow] mapping before returning the entity.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testWrapRowWithAliasedTable
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
        return wrapRow(ResultRow.createAndFillValues(newFieldsMapping))
    }

    /**
     * Wraps the specified [ResultRow] data into an [Entity] instance.
     *
     * The provided [alias] will be used to adjust the [ResultRow] mapping before returning the entity.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.AliasesTests.testWrapRowWithAliasedQuery
     */
    fun wrapRow(row: ResultRow, alias: QueryAlias): T {
        require(alias.columns.any { (it.table as Alias<*>).delegate == table }) { "QueryAlias doesn't have any column from ${table.tableName} table" }
        val originalColumns = alias.query.set.source.columns
        val newFieldsMapping = row.fieldIndex.mapNotNull { (exp, _) ->
            val value = row[exp]
            when {
                exp is Column && exp.table is Alias<*> -> {
                    val delegate = (exp.table as Alias<*>).delegate
                    val column = originalColumns.single {
                        delegate == it.table && exp.name == it.name
                    }
                    column to value
                }

                exp is Column && exp.table == table -> null
                else -> exp to value
            }
        }.toMap()
        return wrapRow(ResultRow.createAndFillValues(newFieldsMapping))
    }

    /**
     * Gets all the [Entity] instances associated with this [EntityClass].
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testNonEntityIdReference
     */
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
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.preloadOptionalReferencesOnAnEntity
     */
    fun find(op: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<T> = find(SqlExpressionBuilder.op())

    /**
     * Searches the current [EntityCache] for all [Entity] instances that match the provided [cacheCheckCondition].
     * If the cache returns no matches, entities that conform to the provided [op] conditional expression
     * will be retrieved from the database.
     *
     * @return A sequence of matching entities found.
     */
    fun findWithCacheCondition(cacheCheckCondition: T.() -> Boolean, op: SqlExpressionBuilder.() -> Op<Boolean>): Sequence<T> {
        val cached = testCache(cacheCheckCondition)
        return if (cached.any()) cached else find(op).asSequence()
    }

    /** The [IdTable] that this [EntityClass] depends on when maintaining relations with managed [Entity] instances. */
    open val dependsOnTables: ColumnSet get() = table

    /** The columns that this [EntityClass] depends on when maintaining relations with managed [Entity] instances. */
    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    /**
     * Returns a [Query] to select all columns in [dependsOnTables] with a WHERE clause that includes
     * the provided [op] conditional expression.
     */
    open fun searchQuery(op: Op<Boolean>): Query =
        dependsOnTables.select(dependsOnColumns).where { op }.setForUpdateStatus()

    /**
     * Counts the amount of [Entity] instances that conform to the [op] conditional expression.
     *
     * @param op The conditional expression to use when selecting the entity.
     * @return The amount of entities that conform to this condition.
     * @sample org.jetbrains.exposed.sql.tests.h2.MultiDatabaseEntityTest.crossReferencesProhibitedForEntitiesFromDifferentDB
     */
    fun count(op: Op<Boolean>? = null): Long {
        val countExpression = when (table) {
            is CompositeIdTable -> table.idColumns.first().count()
            else -> table.id.count()
        }
        val query = table.select(countExpression).notForUpdate()
        op?.let { query.adjustWhere { op } }
        return query.first()[countExpression]
    }

    /** Creates a new [Entity] instance with the provided [entityId] value. */
    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?): T = entityCtor(entityId)

    /**
     * Returns an [Entity] with the provided [EntityID] value, or, if an entity was not found in the current
     * [EntityCache], creates a new instance using the data in [row].
     */
    fun wrap(id: EntityID<ID>, row: ResultRow?): T {
        val transaction = TransactionManager.current()
        return transaction.entityCache.find(this, id) ?: createInstance(id, row).also { new ->
            new.klass = this
            new.db = transaction.db
            warmCache().store(this, new)
        }
    }

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block. The id will be automatically set.
     *
     * @param init The block where the entity's fields can be set.
     * @return The entity that has been created.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testNonEntityIdReference
     */
    open fun new(init: T.() -> Unit) = new(null, init)

    /**
     * Creates a new [Entity] instance with the fields that are set in the [init] block and with the provided [id].
     *
     * @param id The id of the entity. Set this to `null` if it should be automatically generated.
     * @param init The block where the entity's fields can be set.
     * @return The entity that has been created.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testNewIdWithGet
     */
    open fun new(id: ID?, init: T.() -> Unit): T {
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
        if (entityId._value != null || table is CompositeIdTable) {
            when (table) {
                is CompositeIdTable -> (entityId._value as CompositeID).forEach { (column, idValue) ->
                    idValue?.let {
                        prototype.writeValues[column as Column<Any?>] = it
                    }
                }
                else -> prototype.writeValues[table.id as Column<Any?>] = entityId
            }
        }
        try {
            entityCache.addNotInitializedEntityToQueue(prototype)
            prototype.init()
        } finally {
            entityCache.finishEntityInitialization(prototype)
        }
        if (entityId._value == null || (table is CompositeIdTable && (entityId._value as CompositeID).values.any { it == null })) {
            val readValues = prototype._readValues!!
            val writeValues = prototype.writeValues
            table.columns.filter { col ->
                col.defaultValueFun != null && col !in writeValues && readValues.hasValue(col)
            }.forEach { col ->
                writeValues[col as Column<Any?>] = readValues[col]
            }
        }
        entityCache.scheduleInsert(this, prototype)
        return prototype
    }

    /**
     * Creates a [View] or subset of [Entity] instances, which are managed by this [EntityClass] and
     * conform to the specified [op] conditional expression.
     */
    inline fun view(op: SqlExpressionBuilder.() -> Op<Boolean>) = View(SqlExpressionBuilder.op(), this)

    private val refDefinitions = HashMap<Pair<Column<*>, KClass<*>>, Any>()

    private inline fun <reified R : Any> registerRefRule(column: Column<*>, ref: () -> R): R =
        refDefinitions.getOrPut(column to R::class, ref) as R

    /**
     * Registers a reference as a field of the child entity class, which returns a parent object of this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Parent
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Children
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Child
     */
    infix fun <REF : Comparable<REF>> referencedOn(column: Column<REF>) = registerRefRule(column) { Reference(column, this) }

    /**
     * Registers an optional reference as a field of the child entity class, which returns a parent object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Board
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Posts
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Post
     */
    infix fun <REF : Comparable<REF>> optionalReferencedOn(column: Column<REF?>) = registerRefRule(column) { OptionalReference(column, this) }

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.YEntity
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.XTable
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.BEntity
     */
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(
        column: Column<REF>
    ): ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.YEntity
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.XTable
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData.BEntity
     */
    @JvmName("backReferencedOnOpt")
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(
        column: Column<REF?>
    ): ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Student
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.StudentBios
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.StudentBio
     */
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(
        column: Column<REF>
    ) =
        registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column as Column<REF?>, this) }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a child object of
     * this `EntityClass`.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Student
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.StudentBios
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.StudentBio
     */
    @JvmName("optionalBackReferencedOnOpt")
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(
        column: Column<REF?>
    ) =
        registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column, this) }

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * By default, this also stores the loaded entities to a cache.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityHookTestData.Country
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityHookTestData.Cities
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityHookTestData.City
     */
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(column: Column<REF>) =
        registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, true) }

    /**
     * Registers a reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using `reference()` on the child table.
     *
     * Set [cache] to `true` to also store the loaded entities to a cache.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.School
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Students
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Student
     */
    fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(
        column: Column<REF>,
        cache: Boolean
    ) =
        registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * reference().nullable()` on the child table.
     *
     * By default, this also stores the loaded entities to a cache.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Category
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Posts
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Post
     */
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(
        column: Column<REF?>
    ) =
        registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, true) }

    /**
     * Registers an optional reference as an immutable field of the parent entity class, which returns a collection of
     * child objects of this `EntityClass` that all reference the parent.
     *
     * The reference should have been defined by the creation of a [column] using either `optReference()` or
     * `reference().nullable()` on the child table.
     *
     * Set [cache] to `true` to also store the loaded entities to a cache.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Student
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Detentions
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Detention
     */
    fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(
        column: Column<REF?>,
        cache: Boolean = false
    ) =
        registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    /**
     * Returns a [ColumnWithTransform] delegate that transforms this stored [TColumn] value on every read.
     *
     * @param toColumn A pure function that converts a transformed value to a value that can be stored
     * in this original column type.
     * @param toReal A pure function that transforms a value stored in this original column type.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.TransformationsTable
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.TransformationEntity
     */
    fun <TColumn : Any?, TReal : Any?> Column<TColumn>.transform(
        toColumn: (TReal) -> TColumn,
        toReal: (TColumn) -> TReal
    ): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal, false)

    /**
     * Returns a [ColumnWithTransform] delegate that will cache the transformed value on first read of
     * this same stored [TColumn] value.
     *
     * @param toColumn A pure function that converts a transformed value to a value that can be stored
     * in this original column type.
     * @param toReal A pure function that transforms a value stored in this original column type.
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.TransformationsTable
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.TransformationEntity
     */
    fun <TColumn : Any?, TReal : Any?> Column<TColumn>.memoizedTransform(
        toColumn: (TReal) -> TColumn,
        toReal: (TColumn) -> TReal
    ): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal, true)

    private fun Query.setForUpdateStatus(): Query = if (this@EntityClass is ImmutableEntityClass<*, *>) this.notForUpdate() else this

    /**
     * Returns a list of retrieved [Entity] instances whose [refColumn] optionally matches any of the id values in [references].
     *
     * The [EntityCache] in the current transaction scope will be searched for matching entities, if appropriate
     * for [refColumn]'s column type; otherwise, matching results will be queried from the database.
     *
     * Set [forUpdate] to `true` or `false` depending on whether a locking read should be placed or removed from the
     * search query used. Leave the argument as `null` to use the query without any locking option.
     */
    fun <SID> warmUpOptReferences(references: List<SID>, refColumn: Column<SID?>, forUpdate: Boolean? = null): List<T> =
        warmUpReferences(references, refColumn as Column<SID>, forUpdate)

    /**
     * Returns a list of retrieved [Entity] instances whose [refColumn] matches any of the id values in [references].
     *
     * The [EntityCache] in the current transaction scope will be searched for matching entities, if appropriate
     * for [refColumn]'s column type; otherwise, matching results will be queried from the database.
     *
     * Set [forUpdate] to `true` or `false` depending on whether a locking read should be placed or removed from the
     * search query used. Leave the argument as `null` to use the query without any locking option.
     */
    fun <SID> warmUpReferences(references: List<SID>, refColumn: Column<SID>, forUpdate: Boolean? = null): List<T> {
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
            val toLoad = distinctRefIds.filter {
                cache.referrers[refColumn]?.containsKey(it)?.not() ?: true
            }
            if (toLoad.isNotEmpty()) {
                val findQuery = find { refColumn inList toLoad }
                val entities = getEntities(forUpdate, findQuery)

                val result = entities.groupBy { it.readValues[refColumn] }

                distinctRefIds.forEach { id ->
                    cache.getOrPutReferrers(id, refColumn) { result[id]?.let { SizedCollection(it) } ?: emptySized() }.also {
                        if (keepLoadedReferenceOutOfTransaction) {
                            cache.find(this, id)?.storeReferenceInCache(refColumn, it)
                        }
                    }
                }
            }

            return distinctRefIds.flatMap { cache.getReferrers<T>(it, refColumn)?.toList().orEmpty() }
        } else {
            val baseQuery = searchQuery(Op.build { refColumn inList distinctRefIds })
            val finalQuery = if (parentTable.id in baseQuery.set.fields) {
                baseQuery
            } else {
                baseQuery.adjustSelect { select(fields + parentTable.id) }
                    .adjustColumnSet { innerJoin(parentTable, { refColumn }, { refColumn.referee!! }) }
            }

            val findQuery = wrapRows(finalQuery)
            val entities = getEntities(forUpdate, findQuery).distinct()

            entities.groupBy { it.readValues[refColumn] }.forEach { (id, values) ->
                val parentEntityId: EntityID<*> = parentTable.selectAll().where { refColumn.referee as Column<SID> eq id }
                    .single()[parentTable.id]

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

    private fun getEntities(forUpdate: Boolean?, findQuery: SizedIterable<T>): List<T> = when (forUpdate) {
        true -> findQuery.forUpdate()
        false -> findQuery.notForUpdate()
        else -> findQuery
    }.toList()

    @Suppress("ComplexMethod")
    internal fun <SID : Comparable<SID>> warmUpLinkedReferences(
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

        val inCache = transaction.entityCache.referrers[sourceRefColumn] ?: emptyMap()
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
                forEntityIds(entitiesWithRefs.map { it.second }).forEach {
                    targetEntities[it.id] = it
                }
            }

            val groupedBySourceId = entitiesWithRefs.groupBy({ it.first }) { targetEntities.getValue(it.second) }

            idsToLoad.forEach {
                transaction.entityCache.getOrPutReferrers(it, sourceRefColumn) {
                    SizedCollection(groupedBySourceId[it] ?: emptyList())
                }
            }
            targetEntities.values
        }
        return inCache.values.flatMap { it.toList() as List<T> } + loaded.orEmpty()
    }

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
    fun <SID : Comparable<SID>> warmUpLinkedReferences(
        references: List<EntityID<SID>>,
        linkTable: Table,
        forUpdate: Boolean? = null,
        optimizedLoad: Boolean = false
    ): List<T> {
        if (references.isEmpty()) return emptyList()

        val sourceRefColumn = linkTable.columns.singleOrNull { it.referee == references.first().table.id } as? Column<EntityID<SID>>
            ?: error("Can't detect source reference column")
        val targetRefColumn =
            linkTable.columns.singleOrNull { it.referee == table.id } as? Column<EntityID<ID>> ?: error("Can't detect target reference column")

        return warmUpLinkedReferences(references, sourceRefColumn, targetRefColumn, linkTable, forUpdate, optimizedLoad)
    }

    /**
     * Returns whether the [entityClass] type is equivalent to or a superclass of this [EntityClass] instance's [klass].
     */
    fun <ID : Comparable<ID>, T : Entity<ID>> isAssignableTo(entityClass: EntityClass<ID, T>) = entityClass.klass.isAssignableFrom(klass)
}

/**
 * Base class responsible for the management of immutable [Entity] instances and the maintenance of their relation
 * to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities managed by this class.
 * @param [entityType] The expected [Entity] class type. This can be left `null` if it is the class of type
 * argument [T] provided to this [EntityClass] instance.
 * @param [ctor] The function invoked to instantiate an [Entity] using a provided [EntityID] value. If a
 * reference to a specific entity constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ImmutableEntityTest.Schema.Organization
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ImmutableEntityTest.EOrganization
 */
abstract class ImmutableEntityClass<ID : Comparable<ID>, out T : Entity<ID>>(
    table: IdTable<ID>,
    entityType: Class<T>? = null,
    ctor: ((EntityID<ID>) -> T)? = null
) :
    EntityClass<ID, T>(table, entityType, ctor) {
    /**
     * Updates an [entity] field directly in the database, then removes this entity from the [EntityCache] in
     * the current transaction scope. This is useful when needing to ensure that an entity is only updated with
     * data retrieved directly from a database query.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.entities.ImmutableEntityTest.immutableEntityReadAfterUpdate
     */
    open fun <T> forceUpdateEntity(entity: Entity<ID>, column: Column<T>, value: T) {
        table.update({ table.id eq entity.id }) {
            it[column] = value
        }

        /* Evict the entity from the current transaction entity cache,
           so that the next read of this entity using DAO API would return
           actual data from the DB */

        TransactionManager.currentOrNull()?.entityCache?.remove(table, entity)
    }
}

/**
 * Base class responsible for the management of immutable [Entity] instances and the maintenance of their relation
 * to the provided [table].
 * An internal cache is used to store entity loading states by the associated database, in order to guarantee that
 * that entity updates are synchronized with this class as the lock object.
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities managed by this class.
 * @param [entityType] The expected [Entity] class type. This can be left `null` if it is the class of type
 * argument [T] provided to this [EntityClass] instance.
 * @param [ctor] The function invoked to instantiate an [Entity] using a provided [EntityID] value. If a
 * reference to a specific entity constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ImmutableEntityTest.Schema.Organization
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.ImmutableEntityTest.ECachedOrganization
 */
abstract class ImmutableCachedEntityClass<ID : Comparable<ID>, out T : Entity<ID>>(
    table: IdTable<ID>,
    entityType: Class<T>? = null,
    ctor: ((EntityID<ID>) -> T)? = null
) :
    ImmutableEntityClass<ID, T>(table, entityType, ctor) {

    private val cacheLoadingState = Key<Any>()
    private var _cachedValues: MutableMap<Database, MutableMap<Any, Entity<*>>> = ConcurrentHashMap()

    override fun invalidateEntityInCache(o: Entity<ID>) {
        warmCache()
    }

    final override fun warmCache(): EntityCache {
        val tr = TransactionManager.current()
        val db = tr.db
        val transactionCache = super.warmCache()
        if (_cachedValues[db] == null) {
            synchronized(this) {
                val cachedValues = _cachedValues[db]
                when {
                    cachedValues != null -> {
                    } // already loaded in another transaction
                    tr.getUserData(cacheLoadingState) != null -> {
                        return transactionCache // prevent recursive call to warmCache() in .all()
                    }

                    else -> {
                        tr.putUserData(cacheLoadingState, this)
                        super.all().toList() // force iteration to initialize lazy collection
                        _cachedValues[db] = transactionCache.data[table] ?: mutableMapOf()
                        tr.removeUserData(cacheLoadingState)
                    }
                }
            }
        }
        transactionCache.data[table] = _cachedValues[db]!!
        return transactionCache
    }

    override fun all(): SizedIterable<T> = SizedCollection(warmCache().findAll(this))

    /**
     * Clears either only values for the database associated with the current [Transaction] or
     * the entire cache if a database transaction cannot be found.
     */
    @Synchronized
    fun expireCache() {
        if (TransactionManager.isInitialized() && TransactionManager.currentOrNull() != null) {
            _cachedValues.remove(TransactionManager.current().db)
        } else {
            _cachedValues.clear()
        }
    }

    override fun <T> forceUpdateEntity(entity: Entity<ID>, column: Column<T>, value: T) {
        super.forceUpdateEntity(entity, column, value)
        entity._readValues?.set(column, value)
        expireCache()
    }
}
