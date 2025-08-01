package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Class responsible for enabling [Entity] field transformations, which may be useful when advanced database
 * type conversions are necessary for entity mappings.
 */
open class EntityFieldWithTransform<Unwrapped, Wrapped>(
    /** The original column that will be transformed */
    val column: Column<Unwrapped>,
    /** Instance of [ColumnTransformer] with the transformation logic */
    private val transformer: ColumnTransformer<Unwrapped, Wrapped>,
    /**
     * The function used to convert a transformed value to a value that can be stored in the original column type.
     * Whether the original and transformed values should be cached to avoid multiple conversion calls.
     */
    protected val cacheResult: Boolean = false
) : ColumnTransformer<Unwrapped, Wrapped> {
    private var cache: Pair<Unwrapped, Wrapped>? = null

    override fun unwrap(value: Wrapped): Unwrapped {
        return transformer.unwrap(value)
    }

    /** The function used to transform a value stored in the original column type. */
    override fun wrap(value: Unwrapped): Wrapped {
        return if (cacheResult) {
            val localCache = cache
            if (localCache != null && localCache.first == value) {
                localCache.second
            } else {
                transformer.wrap(value).also { cache = value to it }
            }
        } else {
            transformer.wrap(value)
        }
    }
}

/**
 * Class representing a mapping to values stored in a table record in a database.
 *
 * @param id The unique stored identity value for the mapped record.
 */
open class Entity<ID : Any>(val id: EntityID<ID>) {
    /** The associated [EntityClass] that manages this [Entity] instance. */
    var klass: EntityClass<ID, Entity<ID>> by Delegates.notNull()
        internal set

    /** The [Database] associated with the record mapped to this [Entity] instance. */
    var db: Database by Delegates.notNull()
        internal set

    /** The initial column-value mapping for this [Entity] instance before being flushed and inserted into the database. */
    val writeValues = LinkedHashMap<Column<Any?>, Any?>()

    @Suppress("VariableNaming")
    var _readValues: ResultRow? = null

    /** The final column-value mapping for this [Entity] instance after being flushed and retrieved from the database. */
    val readValues: ResultRow
        get() = _readValues ?: run {
            val table = klass.table
            _readValues = klass.searchQuery(Op.build { table.id eq id }).firstOrNull()
                ?: table.selectAll().where { table.id eq id }.first()
            _readValues!!
        }

    private val referenceCache by lazy { HashMap<Column<*>, Any?>() }

    internal fun isNewEntity(): Boolean {
        val cache = TransactionManager.current().entityCache
        return cache.inserts[klass.table]?.contains(this) ?: false
    }

    /**
     * Updates the fields of this [Entity] instance with values retrieved from the database.
     * Override this function to refresh some additional state, if any.
     *
     * @param flush Whether pending entity changes should be flushed prior to updating.
     * @throws EntityNotFoundException If the entity no longer exists in the database.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.testNewWithIdAndRefresh
     */
    open fun refresh(flush: Boolean = false) {
        val transaction = TransactionManager.current()
        val cache = transaction.entityCache
        val isNewEntity = isNewEntity()
        when {
            isNewEntity && flush -> cache.flushInserts(klass.table)
            flush -> flush()
            isNewEntity -> throw EntityNotFoundException(this.id, this.klass)
            else -> writeValues.clear()
        }

        klass.removeFromCache(this)
        val reloaded = klass[id]
        cache.store(this)
        _readValues = reloaded.readValues
        db = transaction.db
    }

    internal fun <T> getReferenceFromCache(ref: Column<*>): T {
        return referenceCache[ref] as T
    }

    internal fun hasInReferenceCache(ref: Column<*>): Boolean {
        return ref in referenceCache
    }

    internal fun storeReferenceInCache(ref: Column<*>, value: Any?) {
        if (db.config.keepLoadedReferencesOutOfTransaction) {
            referenceCache[ref] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <REF : Any, RID : Any, T : Entity<RID>> Reference<REF, RID, T>.getValue(
        o: Entity<ID>,
        desc: KProperty<*>
    ): T {
        val outOfTransaction = TransactionManager.currentOrNull() == null
        if (outOfTransaction && reference in referenceCache) return getReferenceFromCache(reference)
        return executeAsPartOfEntityLifecycle {
            val isSingleIdReference = hasSingleReferenceWithReferee(allReferences)
            val refValue = if (isSingleIdReference) {
                reference.getValue(o, desc)
            } else {
                getCompositeID {
                    allReferences.map { (child, parent) -> parent to child.getValue(o, desc) }
                }
            }
            when {
                refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> {
                    factory.findById(refValue.value as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                refValue is CompositeID && allReferencesMatch(allReferences, factory.table) -> {
                    factory.findById(refValue as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                else -> {
                    val castReferee = reference.referee<REF>()!!
                    val baseReferee = (castReferee.columnType as? EntityIDColumnType<REF>)?.idColumn ?: castReferee
                    factory.findWithCacheCondition({
                        if (isSingleIdReference) {
                            reference.referee!!.getValue(this, desc) == refValue
                        } else {
                            allReferences.all {
                                it.value.getValue(this, desc) == (refValue as CompositeID)[it.key as Column<EntityID<Any>>]
                            }
                        }
                    }) {
                        baseReferee eq (refValue as REF)
                    }.singleOrNull()?.also {
                        storeReferenceInCache(reference, it)
                    }
                }
            } ?: error("Cannot find ${factory.table.tableName} WHERE id=$refValue")
        }
    }

    operator fun <REF : Any, RID : Any, T : Entity<RID>> Reference<REF, RID, T>.setValue(
        o: Entity<ID>,
        desc: KProperty<*>,
        value: T
    ) {
        if (db != value.db) error("Can't link entities from different databases.")
        value.id.value // flush before creating reference on it
        allReferences.forEach { (childColumn, parentColumn) ->
            val refValue = value.run { parentColumn.getValue(this, desc) }
            if (childColumn == reference) storeReferenceInCache(reference, value)
            (childColumn as Column<Any?>).setValue(o, desc, refValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <REF : Any, RID : Any, T : Entity<RID>> OptionalReference<REF, RID, T>.getValue(
        o: Entity<ID>,
        desc: KProperty<*>
    ): T? {
        val outOfTransaction = TransactionManager.currentOrNull() == null
        if (outOfTransaction && reference in referenceCache) return getReferenceFromCache(reference)
        return executeAsPartOfEntityLifecycle {
            val isSingleIdReference = hasSingleReferenceWithReferee(allReferences)
            val refValue = if (isSingleIdReference) {
                reference.getValue(o, desc)
            } else {
                val childValues = allReferences.map { (child, parent) ->
                    parent to child.getValue(o, desc)
                }
                if (childValues.any { it.second == null }) null else getCompositeID { childValues }
            }
            when {
                refValue == null -> null
                refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> {
                    factory.findById(refValue.value as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                refValue is CompositeID && allReferencesMatch(allReferences, factory.table) -> {
                    factory.findById(refValue as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                else -> {
                    factory.findWithCacheCondition({
                        if (isSingleIdReference) {
                            reference.referee!!.getValue(this, desc) == refValue
                        } else {
                            allReferences.all {
                                it.value.getValue(this, desc) == (refValue as CompositeID)[it.key as Column<EntityID<Any>>]
                            }
                        }
                    }) {
                        reference.referee<REF>()!! eq (refValue as REF)
                    }.singleOrNull().also {
                        storeReferenceInCache(reference, it)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <REF : Any, RID : Any, T : Entity<RID>> OptionalReference<REF, RID, T>.setValue(
        o: Entity<ID>,
        desc: KProperty<*>,
        value: T?
    ) {
        if (value != null && db != value.db) error("Can't link entities from different databases.")
        value?.id?.value // flush before creating reference on it
        allReferences.forEach { (childColumn, parentColumn) ->
            val refValue = value?.run { parentColumn.getValue(this, desc) }
            if (childColumn == reference) storeReferenceInCache(reference, value)
            (childColumn as Column<Any?>).setValue(o, desc, refValue)
        }
    }

    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    operator fun <T> CompositeColumn<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T {
        val values = this.getRealColumns().associateWith { it.lookup() }
        return this.restoreValueFromParts(values)
    }

    /**
     * Checks if this column has been assigned a value retrieved from the database, then calls the [found] block
     * with this value as its argument, and returns its result.
     *
     * If a column-value mapping has not been retrieved, the result of calling the [notFound] block is returned instead.
     */
    fun <T, R : Any> Column<T>.lookupInReadValues(found: (T?) -> R?, notFound: () -> R?): R? =
        if (_readValues?.hasValue(this) == true) {
            found(readValues[this])
        } else {
            notFound()
        }

    /**
     * Returns the value assigned to this column mapping.
     *
     * Depending on the state of this [Entity] instance, the value returned may be the initial property assignment,
     * this column's default value, or the value retrieved from the database.
     */
    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    fun <T> Column<T>.lookup(): T = when {
        writeValues.containsKey(this as Column<out Any?>) -> writeValues[this as Column<out Any?>] as T
        id._value == null && _readValues?.hasValue(this)?.not() ?: true -> {
            when {
                isDatabaseGenerated() -> flush().let { readValues[this]!! }
                else -> defaultValueFun?.invoke() as T
            }
        }
        columnType.nullable -> readValues[this]
        else -> readValues[this]!!
    }

    operator fun <T> Column<T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        klass.invalidateEntityInCache(o)
        val currentValue = _readValues?.getOrNull(this)
        if (writeValues.containsKey(this as Column<out Any?>) || currentValue != value) {
            val entityCache = TransactionManager.current().entityCache
            if (referee != null) {
                if (value is EntityID<*> && value.table == referee!!.table) value.value // flush

                listOfNotNull<Any>(value, currentValue).forEach {
                    entityCache.referrers[this]?.remove(it)
                }
            }
            val valueTypeMismatch = value is EntityID<*> && value.table is CompositeIdTable && this.columnType !is EntityIDColumnType<*>
            writeValues[this as Column<Any?>] = if (valueTypeMismatch) (value as EntityID<*>)._value else value
            if (entityCache.data[table].orEmpty().contains(o.id._value)) {
                entityCache.scheduleUpdate(klass, o)
            }
        }
    }

    operator fun <T> CompositeColumn<T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        with(o) {
            this@setValue.getRealColumnsWithValues(value).forEach {
                (it.key as Column<Any?>).setValue(o, desc, it.value)
            }
        }
    }

    operator fun <Unwrapped, Wrapped> EntityFieldWithTransform<Unwrapped, Wrapped>.getValue(o: Entity<ID>, desc: KProperty<*>): Wrapped =
        wrap(column.getValue(o, desc))

    operator fun <Unwrapped, Wrapped> EntityFieldWithTransform<Unwrapped, Wrapped>.setValue(o: Entity<ID>, desc: KProperty<*>, value: Wrapped) {
        column.setValue(o, desc, unwrap(value))
    }

    /**
     * Registers a reference as a field of the child entity class, which returns a parent object of this [EntityClass],
     * for use in many-to-many relations.
     *
     * The reference should have been defined by the creation of a column using `reference()` on an intermediate table.
     *
     * @param table The intermediate table containing reference columns to both child and parent objects.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityHookTestData.User
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityHookTestData.City
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityHookTestData.UsersToCities
     */
    infix fun <TID : Any, Target : Entity<TID>> EntityClass<TID, Target>.via(
        table: Table
    ): InnerTableLink<ID, Entity<ID>, TID, Target> =
        InnerTableLink(table, this@Entity.id.table, this@via)

    /**
     * Registers a reference as a field of the child entity class, which returns a parent object of this [EntityClass],
     * for use in many-to-many relations.
     *
     * The reference should have been defined by the creation of a column using `reference()` on an intermediate table.
     *
     * @param sourceColumn The intermediate table's reference column for the child entity class.
     * @param targetColumn The intermediate table's reference column for the parent entity class.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.ViaTests.NodesTable
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.ViaTests.Node
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.ViaTests.NodeToNodes
     */
    fun <TID : Any, Target : Entity<TID>> EntityClass<TID, Target>.via(
        sourceColumn: Column<EntityID<ID>>,
        targetColumn: Column<EntityID<TID>>
    ) = InnerTableLink(sourceColumn.table, this@Entity.id.table, this@via, sourceColumn, targetColumn)

    /**
     * Deletes this [Entity] instance, both from the cache and from the database.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.testErrorOnSetToDeletedEntity
     */
    open fun delete() {
        val table = klass.table
        // Capture reference to the field
        val entityId = this.id
        TransactionManager.current().registerChange(klass, entityId, EntityChangeType.Removed)
        executeAsPartOfEntityLifecycle {
            table.deleteWhere { table.id eq entityId }
        }
        klass.removeFromCache(this)
    }

    /**
     * Sends all cached inserts and updates for this [Entity] instance to the database.
     *
     * @param batch The [EntityBatchUpdate] instance that should be used to perform a batch update operation
     * for multiple entities. If left `null`, a single update operation will be executed for this entity only.
     * @return `false` if no cached inserts or updates were sent to the database; `true`, otherwise.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityHookTest.testCallingFlushNotifiesEntityHookSubscribers
     */
    open fun flush(batch: EntityBatchUpdate? = null): Boolean {
        if (isNewEntity()) {
            TransactionManager.current().entityCache.flushInserts(this.klass.table)
            return true
        }
        if (writeValues.isNotEmpty()) {
            if (batch == null) {
                val table = klass.table
                // Store values before update to prevent flush inside UpdateStatement

                @Suppress("VariableNaming")
                val _writeValues = writeValues.toMap()
                storeWrittenValues()
                // In case of batch all changes will be registered after all entities flushed
                TransactionManager.current().registerChange(klass, id, EntityChangeType.Updated)
                executeAsPartOfEntityLifecycle {
                    table.update({ table.id eq id }) {
                        for ((c, v) in _writeValues) {
                            it[c] = v
                        }
                    }
                }
            } else {
                batch.addBatch(this)
                for ((c, v) in writeValues) {
                    batch[c] = v
                }
                storeWrittenValues()
            }

            return true
        }
        return false
    }

    /** Transfers initial column-value mappings from [writeValues] to [readValues] and clears the former once complete. */
    fun storeWrittenValues() {
        // move write values to read values
        if (_readValues != null) {
            for ((c, v) in writeValues) {
                val unwrappedValue = if (c.columnType is ColumnWithTransform<*, *>) {
                    (c.columnType as ColumnWithTransform<Any, Any>).unwrapRecursive(v)
                } else {
                    v
                }
                _readValues!![c] = unwrappedValue
            }
            if (klass.dependsOnColumns.any { it.table == klass.table && !_readValues!!.hasValue(it) }) {
                _readValues = null
            }
        }
        // clear write values
        writeValues.clear()
    }

    /**
     * Stores a [value] for a [table] `id` column in this Entity's [writeValues] map.
     * If the `id` column wraps a composite value, each non-null component value is stored for its component column.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun writeIdColumnValue(table: IdTable<*>, value: EntityID<*>) {
        (value._value as? CompositeID)?.let { id ->
            writeCompositeIdColumnValue(table, id)
            value._value = null
        } ?: run {
            writeValues[table.id as Column<Any?>] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeCompositeIdColumnValue(table: IdTable<*>, id: CompositeID) {
        table.idColumns.forEach { column ->
            val wrappedIdColumnType = (column.columnType as EntityIDColumnType<*>).idColumn.columnType
            if (wrappedIdColumnType !is AutoIncColumnType<*> && column.defaultValueFun == null && column !in id) {
                error("Required column $column is not set to composite id")
            }
            if (column in id) { // so we skip autoincrement columns and autogenerated columns
                id[column as Column<EntityID<Any>>]?.let {
                    writeValues[column as Column<Any?>] = it
                }
            }
        }
    }
}
