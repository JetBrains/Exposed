package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor
import kotlin.sequences.Sequence
import kotlin.sequences.any
import kotlin.sequences.filter

@Suppress("UNCHECKED_CAST", "UnnecessaryAbstractClass", "TooManyFunctions")
abstract class EntityClass<ID : Comparable<ID>, out T : Entity<ID>>(
    val table: IdTable<ID>,
    entityType: Class<T>? = null,

    /**
     * A function that creates an entity instance; typically, you can pass a reference to the entity constructor.
     * If not given, reflection will be used to create instances, which is somewhat slower, especially the first time.
     */
    entityCtor: ((EntityID<ID>) -> T)? = null,
) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>

    private val entityPrimaryCtor: KFunction<T> by lazy { klass.kotlin.primaryConstructor as KFunction<T> }

    private val entityCtor: (EntityID<ID>) -> T = entityCtor ?: { entityID -> entityPrimaryCtor.call(entityID) }

    operator fun get(id: EntityID<ID>): T = findById(id) ?: throw EntityNotFoundException(id, this)

    operator fun get(id: ID): T = get(DaoEntityID(id, table))

    protected open fun warmCache(): EntityCache = TransactionManager.current().entityCache

    /**
     * Get an entity by its [id].
     *
     * @param id The id of the entity
     *
     * @return The entity that has this id or null if no entity was found.
     */
    fun findById(id: ID): T? = findById(DaoEntityID(id, table))

    /**
     * Get an entity by its [id].
     *
     * @param id The id of the entity
     *
     * @return The entity that has this id or null if no entity was found.
     */
    open fun findById(id: EntityID<ID>): T? = testCache(id) ?: find { table.id eq id }.firstOrNull()

    /**
     * Reloads entity fields from database as new object.
     * @param flush whether pending entity changes should be flushed previously
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
        if (entityAlreadyFlushed && sameDatabase) {
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
    }

    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    fun testCache(cacheCheckCondition: T.() -> Boolean): Sequence<T> = warmCache().findAll(this).asSequence().filter { it.cacheCheckCondition() }

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

    open fun forEntityIds(ids: List<EntityID<ID>>): SizedIterable<T> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptySized()

        val cached = distinctIds.mapNotNull { testCache(it) }

        if (cached.size == distinctIds.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(Op.build { table.id inList distinctIds }))
    }

    fun forIds(ids: List<ID>): SizedIterable<T> = forEntityIds(ids.map { DaoEntityID(it, table) })

    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    fun wrapRows(rows: SizedIterable<ResultRow>, alias: Alias<IdTable<*>>) = rows mapLazy {
        wrapRow(it, alias)
    }

    fun wrapRows(rows: SizedIterable<ResultRow>, alias: QueryAlias) = rows mapLazy {
        wrapRow(it, alias)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun wrapRow(row: ResultRow): T {
        val entity = wrap(row[table.id], row)
        if (entity._readValues == null) {
            entity._readValues = row
        }

        return entity
    }

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

    open fun all(): SizedIterable<T> = wrapRows(table.selectAll().notForUpdate())

    /**
     * Get all the entities that conform to the [op] statement.
     *
     * @param op The statement to select the entities for. The statement must be of boolean type.
     *
     * @return All the entities that conform to the [op] statement.
     */
    fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    /**
     * Get all the entities that conform to the [op] statement.
     *
     * @param op The statement to select the entities for. The statement must be of boolean type.
     *
     * @return All the entities that conform to the [op] statement.
     */
    fun find(op: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<T> = find(SqlExpressionBuilder.op())

    fun findWithCacheCondition(cacheCheckCondition: T.() -> Boolean, op: SqlExpressionBuilder.() -> Op<Boolean>): Sequence<T> {
        val cached = testCache(cacheCheckCondition)
        return if (cached.any()) cached else find(op).asSequence()
    }

    open val dependsOnTables: ColumnSet get() = table
    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    open fun searchQuery(op: Op<Boolean>): Query =
        dependsOnTables.slice(dependsOnColumns).select { op }.setForUpdateStatus()

    /**
     * Count the amount of entities that conform to the [op] statement.
     *
     * @param op The statement to count the entities for. The statement must be of boolean type.
     *
     * @return The amount of entities that conform to the [op] statement.
     */
    fun count(op: Op<Boolean>? = null): Long {
        val countExpression = table.id.count()
        val query = table.slice(countExpression).selectAll().notForUpdate()
        op?.let { query.adjustWhere { op } }
        return query.first()[countExpression]
    }

    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?): T = entityCtor(entityId)

    fun wrap(id: EntityID<ID>, row: ResultRow?): T {
        val transaction = TransactionManager.current()
        return transaction.entityCache.find(this, id) ?: createInstance(id, row).also { new ->
            new.klass = this
            new.db = transaction.db
            warmCache().store(this, new)
        }
    }

    /**
     * Create a new entity with the fields that are set in the [init] block. The id will be automatically set.
     *
     * @param init The block where the entities' fields can be set.
     *
     * @return The entity that has been created.
     */
    open fun new(init: T.() -> Unit) = new(null, init)

    /**
     * Create a new entity with the fields that are set in the [init] block and with a set [id].
     *
     * @param id The id of the entity. Set this to null if it should be automatically generated.
     * @param init The block where the entities' fields can be set.
     *
     * @return The entity that has been created.
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
        if (entityId._value != null) {
            prototype.writeValues[table.id as Column<Any?>] = entityId
        }
        try {
            entityCache.addNotInitializedEntityToQueue(prototype)
            prototype.init()
        } finally {
            entityCache.finishEntityInitialization(prototype)
        }
        if (entityId._value == null) {
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

    inline fun view(op: SqlExpressionBuilder.() -> Op<Boolean>) = View(SqlExpressionBuilder.op(), this)

    private val refDefinitions = HashMap<Pair<Column<*>, KClass<*>>, Any>()

    private inline fun <reified R : Any> registerRefRule(column: Column<*>, ref: () -> R): R =
        refDefinitions.getOrPut(column to R::class, ref) as R

    infix fun <REF : Comparable<REF>> referencedOn(column: Column<REF>) = registerRefRule(column) { Reference(column, this) }

    infix fun <REF : Comparable<REF>> optionalReferencedOn(column: Column<REF?>) = registerRefRule(column) { OptionalReference(column, this) }

    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(
        column: Column<REF>
    ):
        ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    @JvmName("backReferencedOnOpt")
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(
        column: Column<REF?>
    ):
        ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(
        column: Column<REF>
    ) =
        registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column as Column<REF?>, this) }

    @JvmName("optionalBackReferencedOnOpt")
    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(
        column: Column<REF?>
    ) =
        registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column, this) }

    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(column: Column<REF>) =
        registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, true) }

    fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(
        column: Column<REF>,
        cache: Boolean
    ) =
        registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    infix fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(
        column: Column<REF?>
    ) =
        registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, true) }

    fun <TargetID : Comparable<TargetID>, Target : Entity<TargetID>, REF : Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(
        column: Column<REF?>,
        cache: Boolean = false
    ) =
        registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    fun <TColumn : Any?, TReal : Any?> Column<TColumn>.transform(
        toColumn: (TReal) -> TColumn,
        toReal: (TColumn) -> TReal
    ): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal, false)

    /**
     * Function will return [ColumnWithTransform] delegate that will cache value on read for the same [TColumn] value.
     * @param toReal should be pure function
     */
    fun <TColumn : Any?, TReal : Any?> Column<TColumn>.memoizedTransform(
        toColumn: (TReal) -> TColumn,
        toReal: (TColumn) -> TReal
    ): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal, true)

    private fun Query.setForUpdateStatus(): Query = if (this@EntityClass is ImmutableEntityClass<*, *>) this.notForUpdate() else this

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun <SID> warmUpOptReferences(references: List<SID>, refColumn: Column<SID?>, forUpdate: Boolean? = null): List<T> =
        warmUpReferences(references, refColumn as Column<SID>, forUpdate)

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
                            findById(id)?.storeReferenceInCache(refColumn, it)
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
                baseQuery.adjustSlice { slice(this.fields + parentTable.id) }
                    .adjustColumnSet { innerJoin(parentTable, { refColumn }, { refColumn.referee!! }) }
            }

            val findQuery = wrapRows(finalQuery)
            val entities = getEntities(forUpdate, findQuery).distinct()

            entities.groupBy { it.readValues[parentTable.id] }.forEach { (id, values) ->
                cache.getOrPutReferrers(id, refColumn) { SizedCollection(values) }.also {
                    if (keepLoadedReferenceOutOfTransaction) {
                        findById(id as EntityID<ID>)?.storeReferenceInCache(refColumn, it)
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

    internal fun <SID: Comparable<SID>> warmUpLinkedReferences(
        references: List<EntityID<SID>>, sourceRefColumn: Column<EntityID<SID>>, targetRefColumn: Column<EntityID<ID>>, linkTable: Table,
        forUpdate: Boolean? = null, optimizedLoad: Boolean = false
    ) : List<T> {
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

            val query = entityTables.slice(columns).select { sourceRefColumn inList idsToLoad }
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
     * @param optimizedLoad will force to make to two queries to load ids and referenced entities separately.
     * Can be useful when references target the same entities. That will prevent from loading them multiple times (per each reference row) and will require
     * less memory/bandwidth for "heavy" entities (with a lot of columns or columns with huge data in it)
     */
    fun <SID: Comparable<SID>> warmUpLinkedReferences(references: List<EntityID<SID>>, linkTable: Table, forUpdate: Boolean? = null, optimizedLoad: Boolean = false): List<T> {
        if (references.isEmpty()) return emptyList()

        val sourceRefColumn = linkTable.columns.singleOrNull { it.referee == references.first().table.id } as? Column<EntityID<SID>>
            ?: error("Can't detect source reference column")
        val targetRefColumn =
            linkTable.columns.singleOrNull { it.referee == table.id } as? Column<EntityID<ID>> ?: error("Can't detect target reference column")

        return warmUpLinkedReferences(references, sourceRefColumn, targetRefColumn, linkTable, forUpdate, optimizedLoad)
    }

    fun <ID : Comparable<ID>, T : Entity<ID>> isAssignableTo(entityClass: EntityClass<ID, T>) = entityClass.klass.isAssignableFrom(klass)
}



abstract class ImmutableEntityClass<ID : Comparable<ID>, out T : Entity<ID>>(
    table: IdTable<ID>,
    entityType: Class<T>? = null,
    ctor: ((EntityID<ID>) -> T)? = null
) :
    EntityClass<ID, T>(table, entityType, ctor) {
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
        if (_cachedValues[db] == null) synchronized(this) {
            val cachedValues = _cachedValues[db]
            when {
                cachedValues != null -> {
                } // already loaded in another transaction
                tr.getUserData(cacheLoadingState) != null -> {
                    return transactionCache // prevent recursive call to warmCache() in .all()
                }
                else -> {
                    tr.putUserData(cacheLoadingState, this)
                    super.all().toList() /* force iteration to initialize lazy collection */
                    _cachedValues[db] = transactionCache.data[table] ?: mutableMapOf()
                    tr.removeUserData(cacheLoadingState)
                }
            }
        }
        transactionCache.data[table] = _cachedValues[db]!!
        return transactionCache
    }

    override fun all(): SizedIterable<T> = SizedCollection(warmCache().findAll(this))

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
