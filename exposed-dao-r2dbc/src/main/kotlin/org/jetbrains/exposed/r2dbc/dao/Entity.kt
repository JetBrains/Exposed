package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.r2dbc.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.r2dbc.dao.relationships.InnerTableLink
import org.jetbrains.exposed.v1.core.AutoIncColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CompositeColumn
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.collections.get
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Class representing a mapping to values stored in a table record in a database.
 *
 * @param id The unique stored identity value for the mapped record.
 */
@ExperimentalR2dbcDaoApi
open class Entity<ID : Any>(val id: EntityID<ID>) {

    /** The associated [EntityClass] that manages this [Entity] instance. */
    var klass: EntityClass<ID, Entity<ID>> by Delegates.notNull()
        internal set

    /** The [R2dbcDatabase] associated with the record mapped to this [Entity] instance. */
    var db: R2dbcDatabase by Delegates.notNull()
        internal set

    /**
     * The initial column-value mapping for this [Entity] instance before being flushed and inserted into the database.
     *
     * These values are transferred to [readValues] before being sent to the database during a flush operation.
     * In case of a transaction failure, both [writeValues] and [readValues] are cleared before rollback
     * to ensure that no stale data is carried over into a new transaction.
     */
    val writeValues = LinkedHashMap<Column<Any?>, Any?>()

    @Suppress("VariableNaming")
    var _readValues: ResultRow? = null

    /** The final column-value mapping for this [Entity] instance after being flushed and retrieved from the database. */
    val readValues: ResultRow
        get() = _readValues ?: error("Entity is not initialized yet. Call flush() or reload the entity from the database.")

    private val referenceCache by lazy { HashMap<Column<*>, Any?>() }

    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    /**
     * Returns the value assigned to this column mapping.
     *
     * Depending on the state of this [Entity] instance, the value returned may be the initial property assignment,
     * this column's default value, or the value retrieved from the database.
     */
    fun <T> Column<T>.lookup(): T = when {
        writeValues.containsKey(this as Column<out Any?>) -> writeValues[this as Column<out Any?>] as T
        id._value == null && _readValues?.hasValue(this)?.not() ?: true -> {
            when {
                isDatabaseGenerated() -> error(
                    "Cannot access database-generated column $name before flush. " +
                        "Call suspend flush() first to retrieve generated values."
                )
                else -> defaultValueFun?.invoke() as T
            }
        }
        else -> readValues[this]
    }

    operator fun <T> Column<T>.setValue(entity: Entity<ID>, desc: KProperty<*>, value: T) {
        klass.invalidateEntityInCache(entity)
        val currentValue = _readValues?.getOrNull(this)
        if (writeValues.containsKey(this as Column<out Any?>) || currentValue != value) {
            val entityCache = TransactionManager.current().entityCache

            val valueTypeMismatch = value is EntityID<*> && value.table is CompositeIdTable && this.columnType !is EntityIDColumnType<*>
            writeValues[this as Column<Any?>] = if (valueTypeMismatch) (value as EntityID<*>)._value else value

            if (entity.id._value != null) {
                @Suppress("UNCHECKED_CAST")
                val entityTable = this.table as? IdTable<Any> ?: klass.table as IdTable<Any>
                if (entityCache.data[entityTable].orEmpty().contains(entity.id._value)) {
                    entityCache.scheduleUpdate(klass, entity)
                }
            }
        }
    }

    /**
     * Property delegate for [CompositeColumn] — reads each underlying column's value via [Column.lookup]
     * and reassembles them via [CompositeColumn.restoreValueFromParts]. Mirrors JDBC's `Entity` operator.
     */
    operator fun <T> CompositeColumn<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T {
        val values = this.getRealColumns().associateWith { it.lookup() }
        return this.restoreValueFromParts(values)
    }

    /**
     * Property delegate for [CompositeColumn] — splits [value] into its real-column parts via
     * [CompositeColumn.getRealColumnsWithValues] and writes each part through [Column.setValue].
     * Mirrors JDBC's `Entity` operator.
     */
    operator fun <T> CompositeColumn<T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        with(o) {
            this@setValue.getRealColumnsWithValues(value).forEach { (column, partValue) ->
                @Suppress("UNCHECKED_CAST")
                (column as Column<Any?>).setValue(o, desc, partValue)
            }
        }
    }

    /**
     * Property delegate for [EntityFieldWithTransform] — reads the raw column value via [Column.getValue]
     * and runs it through the transformer's `wrap` function (with optional memoization).
     */
    operator fun <Unwrapped, Wrapped> EntityFieldWithTransform<Unwrapped, Wrapped>.getValue(o: Entity<ID>, desc: KProperty<*>): Wrapped =
        wrap(column.getValue(o, desc))

    /**
     * Property delegate for [EntityFieldWithTransform] — runs the supplied value through the transformer's
     * `unwrap` function and writes it back to the original column via [Column.setValue].
     */
    operator fun <Unwrapped, Wrapped> EntityFieldWithTransform<Unwrapped, Wrapped>.setValue(o: Entity<ID>, desc: KProperty<*>, value: Wrapped) {
        column.setValue(o, desc, unwrap(value))
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

    internal fun isNewEntity(): Boolean {
        val cache = TransactionManager.current().entityCache
        return cache.inserts[klass.table]?.contains(this) ?: false
    }

    /** Transfers initial column-value mappings from [writeValues] to [readValues] and clears the former once complete. */
    fun storeWrittenValues() {
        // Move write values to read values
        if (_readValues != null) {
            for ((c, v) in writeValues) {
                _readValues!![c] = v
            }
            // Clear _readValues if not all columns are loaded
            if (klass.dependsOnColumns.any { it.table == klass.table && !_readValues!!.hasValue(it) }) {
                _readValues = null
            }
        }
        // Clear write values
        writeValues.clear()
    }

    /**
     * Sends all cached inserts and updates for this [Entity] instance to the database.
     *
     * @param batch The [EntityBatchUpdate] instance that should be used to perform a batch update operation
     * for multiple entities. If left `null`, a single update operation will be executed for this entity only.
     * @return `false` if no cached inserts or updates were sent to the database; `true`, otherwise.
     */
    @Suppress("ForbiddenComment")
    open suspend fun flush(batch: EntityBatchUpdate? = null): Boolean {
        if (isNewEntity()) {
            TransactionManager.current().entityCache.flushInserts(klass.table)
            return true
        }
        if (writeValues.isNotEmpty()) {
            if (batch == null) {
                val table = klass.table

                @Suppress("VariableNaming")
                val _writeValues = writeValues.toMap()
                storeWrittenValues()

                val transaction = TransactionManager.current()

                @Suppress("UNCHECKED_CAST")
                transaction.registerChange(klass as EntityClass<*, Entity<*>>, id, EntityChangeType.Updated)

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

    /**
     * Deletes this [Entity] instance, both from the cache and from the database.
     *
     * For entities that have not yet been flushed (i.e. still scheduled for insert), no DELETE statement
     * is issued — the entity is simply removed from the scheduled inserts. This differs from JDBC, which
     * issues an INSERT followed by a DELETE.
     */
    open suspend fun delete() {
        val table = klass.table
        val entityId = this.id

        // This behaves differently from the JDBC module. In JDBC, the entity is inserted first and then
        // removed from the database. Here we don't do that at the moment, and just remove it from cache if it was not inserted yet.
        if (!isNewEntity()) {
            val transaction = TransactionManager.current()

            @Suppress("UNCHECKED_CAST")
            transaction.registerChange(klass as EntityClass<*, Entity<*>>, entityId, EntityChangeType.Removed)

            executeAsPartOfEntityLifecycle {
                table.deleteWhere { table.id eq entityId }
            }
        }

        klass.removeFromCache(this)
    }

    internal fun hasInReferenceCache(ref: Column<*>): Boolean {
        return ref in referenceCache
    }

    internal fun <T> getReferenceFromCache(ref: Column<*>): T {
        return referenceCache[ref] as T
    }

    @Suppress("UNCHECKED_CAST")
    internal fun resolveColumnValue(column: Column<*>): Any? =
        writeValues[column as Column<Any?>]
            ?: _readValues?.getOrNull(column)

    internal fun storeReferenceInCache(ref: Column<*>, value: Any?) {
        if (db.config.keepLoadedReferencesOutOfTransaction) {
            referenceCache[ref] = value
        }
    }

    /**
     * Updates the fields of this [Entity] instance with values retrieved from the database.
     * Override this function to refresh some additional state, if any.
     *
     * @param flush Whether pending entity changes should be flushed prior to updating.
     * @throws EntityNotFoundException If the entity no longer exists in the database.
     */
    open suspend fun refresh(flush: Boolean = false) {
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

    /**
     * Registers an intermediate [table] as a many-to-many link between this entity's table and
     * the target [EntityClass]. The source and target columns are inferred from the
     * intermediate table's foreign keys.
     *
     * Counterpart of JDBC's `via`.
     */
    infix fun <TID : Any, Target : Entity<TID>> EntityClass<TID, Target>.via(
        table: Table
    ): InnerTableLink<ID, Entity<ID>, TID, Target> =
        InnerTableLink(
            table = table,
            sourceTable = this@Entity.id.table,
            target = this@via
        )

    /**
     * Registers an intermediate table as a many-to-many link with explicitly specified
     * [sourceColumn] and [targetColumn] — use this when the intermediate table has multiple
     * references into the same entity's table and the defaults cannot be inferred.
     */
    fun <TID : Any, Target : Entity<TID>> EntityClass<TID, Target>.via(
        sourceColumn: Column<EntityID<ID>>,
        targetColumn: Column<EntityID<TID>>
    ): InnerTableLink<ID, Entity<ID>, TID, Target> =
        InnerTableLink(
            table = sourceColumn.table,
            sourceTable = this@Entity.id.table,
            target = this@via,
            _sourceColumn = sourceColumn,
            _targetColumn = targetColumn
        )
}
