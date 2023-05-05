package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

open class ColumnWithTransform<TColumn, TReal>(
    val column: Column<TColumn>,
    val toColumn: (TReal) -> TColumn,
    toReal: (TColumn) -> TReal,
    protected val cacheResult: Boolean = false
) {
    private var cache: Pair<TColumn, TReal>? = null

    val toReal: (TColumn) -> TReal = { columnValue ->
        if (cacheResult) {
            val localCache = cache
            if (localCache != null && localCache.first == columnValue) {
                localCache.second
            } else {
                toReal(columnValue).also { cache = columnValue to it }
            }
        } else {
            toReal(columnValue)
        }
    }
}

open class Entity<ID : Comparable<ID>>(val id: EntityID<ID>) {
    var klass: EntityClass<ID, Entity<ID>> by Delegates.notNull()
        internal set

    var db: Database by Delegates.notNull()
        internal set

    val writeValues = LinkedHashMap<Column<Any?>, Any?>()
    var _readValues: ResultRow? = null
    val readValues: ResultRow
        get() = _readValues ?: run {
            val table = klass.table
            _readValues = klass.searchQuery(Op.build { table.id eq id }).firstOrNull() ?: table.select { table.id eq id }.first()
            _readValues!!
        }

    private val referenceCache by lazy { HashMap<Column<*>, Any?>() }

    internal fun isNewEntity(): Boolean {
        val cache = TransactionManager.current().entityCache
        return cache.inserts[klass.table]?.contains(this) ?: false
    }

    /**
     * Updates entity fields from database.
     * Override function to refresh some additional state if any.
     *
     * @param flush whether pending entity changes should be flushed previously
     * @throws EntityNotFoundException if entity no longer exists in database
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

    internal fun storeReferenceInCache(ref: Column<*>, value: Any?) {
        if (db.config.keepLoadedReferencesOutOfTransaction) {
            referenceCache[ref] = value
        }
    }

    operator fun <REF : Comparable<REF>, RID : Comparable<RID>, T : Entity<RID>> Reference<REF, RID, T>.getValue(
        o: Entity<ID>,
        desc: KProperty<*>
    ): T {
        val outOfTransaction = TransactionManager.currentOrNull() == null
        if (outOfTransaction && reference in referenceCache) return getReferenceFromCache(reference)
        return executeAsPartOfEntityLifecycle {
            val refValue = reference.getValue(o, desc)
            when {
                refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> {
                    factory.findById(refValue.value as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                else -> {
                    // @formatter:off
                    factory.findWithCacheCondition({
                       reference.referee!!.getValue(this, desc) == refValue
                    }) {
                        reference.referee<REF>()!! eq refValue
                    }.singleOrNull()?.also {
                        storeReferenceInCache(reference, it)
                    }
                    // @formatter:on
                }
            } ?: error("Cannot find ${factory.table.tableName} WHERE id=$refValue")
        }
    }

    operator fun <REF : Comparable<REF>, RID : Comparable<RID>, T : Entity<RID>> Reference<REF, RID, T>.setValue(
        o: Entity<ID>,
        desc: KProperty<*>,
        value: T
    ) {
        if (db != value.db) error("Can't link entities from different databases.")
        value.id.value // flush before creating reference on it
        val refValue = value.run { reference.referee<REF>()!!.getValue(this, desc) }
        storeReferenceInCache(reference, value)
        reference.setValue(o, desc, refValue)
    }

    operator fun <REF : Comparable<REF>, RID : Comparable<RID>, T : Entity<RID>> OptionalReference<REF, RID, T>.getValue(
        o: Entity<ID>,
        desc: KProperty<*>
    ): T? {
        val outOfTransaction = TransactionManager.currentOrNull() == null
        if (outOfTransaction && reference in referenceCache) return getReferenceFromCache(reference)
        return executeAsPartOfEntityLifecycle {
            val refValue = reference.getValue(o, desc)
            when {
                refValue == null -> null
                refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> {
                    factory.findById(refValue.value as RID).also {
                        storeReferenceInCache(reference, it)
                    }
                }
                else -> {
                    // @formatter:off
                   factory.findWithCacheCondition({
                       reference.referee!!.getValue(this, desc) == refValue
                   }) {
                       reference.referee<REF>()!! eq refValue
                   }.singleOrNull().also {
                       storeReferenceInCache(reference, it)
                   }
                    // @formatter:on
                }
            }
        }
    }

    operator fun <REF : Comparable<REF>, RID : Comparable<RID>, T : Entity<RID>> OptionalReference<REF, RID, T>.setValue(
        o: Entity<ID>,
        desc: KProperty<*>,
        value: T?
    ) {
        if (value != null && db != value.db) error("Can't link entities from different databases.")
        value?.id?.value // flush before creating reference on it
        val refValue = value?.run { reference.referee<REF>()!!.getValue(this, desc) }
        storeReferenceInCache(reference, value)
        reference.setValue(o, desc, refValue)
    }

    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    operator fun <T> CompositeColumn<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T {
        val values = this.getRealColumns().associateWith { it.lookup() }
        return this.restoreValueFromParts(values)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T, R : Any> Column<T>.lookupInReadValues(found: (T?) -> R?, notFound: () -> R?): R? =
        if (_readValues?.hasValue(this) == true) {
            found(readValues[this])
        } else {
            notFound()
        }

    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    fun <T> Column<T>.lookup(): T = when {
        writeValues.containsKey(this as Column<out Any?>) -> writeValues[this as Column<out Any?>] as T
        id._value == null && _readValues?.hasValue(this)?.not() ?: true -> defaultValueFun?.invoke() as T
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
            writeValues[this as Column<Any?>] = value
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

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.getValue(o: Entity<ID>, desc: KProperty<*>): TReal =
        toReal(column.getValue(o, desc))

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.setValue(o: Entity<ID>, desc: KProperty<*>, value: TReal) {
        column.setValue(o, desc, toColumn(value))
    }

    infix fun <TID : Comparable<TID>, Target : Entity<TID>> EntityClass<TID, Target>.via(table: Table): InnerTableLink<ID, Entity<ID>, TID, Target> =
        InnerTableLink(table, this@Entity.id.table, this@via)

    fun <TID : Comparable<TID>, Target : Entity<TID>> EntityClass<TID, Target>.via(
        sourceColumn: Column<EntityID<ID>>,
        targetColumn: Column<EntityID<TID>>
    ) = InnerTableLink(sourceColumn.table, this@Entity.id.table, this@via, sourceColumn, targetColumn)

    /**
     * Delete this entity.
     *
     * This will remove the entity from the database as well as the cache.
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

    open fun flush(batch: EntityBatchUpdate? = null): Boolean {
        if (isNewEntity()) {
            TransactionManager.current().entityCache.flushInserts(this.klass.table)
            return true
        }
        if (writeValues.isNotEmpty()) {
            if (batch == null) {
                val table = klass.table
                // Store values before update to prevent flush inside UpdateStatement
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

    fun storeWrittenValues() {
        // move write values to read values
        if (_readValues != null) {
            for ((c, v) in writeValues) {
                _readValues!![c] = v
            }
            if (klass.dependsOnColumns.any { it.table == klass.table && !_readValues!!.hasValue(it) }) {
                _readValues = null
            }
        }
        // clear write values
        writeValues.clear()
    }
}
