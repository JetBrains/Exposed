package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.AutoIncColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CompositeColumn
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.r2dbc.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.dao.r2dbc.relationships.InnerTableLink
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.update
import java.util.concurrent.atomic.AtomicInteger
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
     * This entity's committed column values. Values a transaction has staged but not committed are held
     * by that transaction's [EntityCache] instead.
     */
    @Suppress("VariableNaming")
    var _readValues: ResultRow? = null

    /** The final column-value mapping for this [Entity] instance after being flushed and retrieved from the database. */
    val readValues: ResultRow
        get() = _readValues ?: error(
            "Entity has no committed values: it is not initialized yet, or its row was created by a " +
                "transaction that has not committed. Call flush() or refresh() to load it from the database."
        )

    private val cache: EntityCache
        get() = (currentR2dbcTransactionOrNull() ?: TransactionManager.current()).entityCache

    /** Records [value] as this entity's value for [column] in the current transaction's [EntityCache]. */
    internal fun stageWrite(column: Column<Any?>, value: Any?) {
        cache.stageWrite(this, column, value)
    }

    internal fun updateByCommittedValues(values: Map<Column<Any?>, Any?>) {
        val committed = _readValues
        if (committed != null && values.keys.all { it in committed.fieldIndex }) {
            for ((column, value) in unwrapColumnValues(values)) {
                committed[column] = value
            }
            return
        }

        val merged = LinkedHashMap<Expression<*>, Any?>()
        committed?.fieldIndex?.keys?.forEach { merged[it] = committed.getOrNull(it) }
        merged.putAll(values)
        _readValues = ResultRow.createAndFillValues(unwrapColumnValues(merged))
    }

    /**
     * How many cache scopes hold staged values for this entity. Zero lets a read go straight to the
     * committed snapshot; too high only costs a lookup, so it is safe to leave stale that way.
     */
    internal val stagedScopeCount = AtomicInteger(0)

    /**
     * The single scope's staged values, when exactly one scope stages for this entity, paired with the
     * transaction that owns them.
     */
    internal var stagedMemo: StagedMemo? = null

    private val referenceCache by lazy { HashMap<Column<*>, Any?>() }

    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    private fun stagedValueOrNone(column: Column<Any?>): Any? {
        val stagingScopes = stagedScopeCount.get()
        if (stagingScopes == 0) return NoStagedValue

        val current = currentR2dbcTransactionOrNull() ?: return NoStagedValue

        val memo = stagedMemo
        if (stagingScopes == 1 && memo != null && memo.owner === current) {
            return memo.values.valueOrNone(column)
        }

        return current.entityCache.stagedValue(this, column)
    }

    /**
     * Returns the value assigned to this column mapping.
     *
     * Depending on the state of this [Entity] instance, the value returned may be the initial property assignment,
     * this column's default value, or the value retrieved from the database.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> Column<T>.lookup(): T {
        val staged = stagedValueOrNone(this as Column<Any?>)
        if (staged !== NoStagedValue) return staged as T

        return when {
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
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> Column<T>.setValue(entity: Entity<ID>, desc: KProperty<*>, value: T) {
        klass.invalidateEntityInCache(entity)
        val entityCache = (currentR2dbcTransactionOrNull() ?: TransactionManager.current()).entityCache
        val column = this as Column<Any?>

        val alreadyDirty = entityCache.isDirty(entity, column)
        val currentValue = if (alreadyDirty) {
            null
        } else {
            val staged = entityCache.stagedValue(entity, column)
            if (staged === NoStagedValue) _readValues?.getOrNull(this) else staged
        }

        if (alreadyDirty || currentValue != value) {
            val valueTypeMismatch = value is EntityID<*> && value.table is CompositeIdTable && this.columnType !is EntityIDColumnType<*>
            entityCache.stageWrite(entity, column, if (valueTypeMismatch) (value as EntityID<*>)._value else value)

            if (entity.id._value != null) {
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
     * Stages a [value] for a [table] `id` column in the current transaction's [EntityCache].
     * If the `id` column wraps a composite value, each non-null component value is stored for its component column.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun writeIdColumnValue(table: IdTable<*>, value: EntityID<*>) {
        (value._value as? CompositeID)?.let { id ->
            writeCompositeIdColumnValue(table, id)
            value._value = null
        } ?: run {
            stageWrite(table.id as Column<Any?>, value)
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
                    stageWrite(column as Column<Any?>, it)
                }
            }
        }
    }

    internal fun isNewEntity(): Boolean {
        val cache = TransactionManager.current().entityCache
        return cache.inserts[klass.table]?.contains(this) ?: false
    }

    /**
     * Sends all cached inserts and updates for this [Entity] instance to the database.
     *
     * @param batch The [EntityBatchUpdate] instance that should be used to perform a batch update operation
     * for multiple entities. If left `null`, a single update operation will be executed for this entity only.
     * @return `false` if no cached inserts or updates were sent to the database; `true`, otherwise.
     */
    open suspend fun flush(batch: EntityBatchUpdate? = null): Boolean {
        val transaction = TransactionManager.current()
        val entityCache = transaction.entityCache

        if (isNewEntity()) {
            entityCache.flushInserts(klass.table)
            return true
        }

        val pending = entityCache.dirtyValues(this).toMap()
        if (pending.isEmpty()) return false

        entityCache.markFlushed(this)

        if (batch == null) {
            val table = klass.table

            @Suppress("UNCHECKED_CAST")
            transaction.registerChange(klass as EntityClass<*, Entity<*>>, id, EntityChangeType.Updated)

            executeAsPartOfEntityLifecycle {
                table.update({ table.id eq id }) {
                    for ((c, v) in pending) {
                        it[c] = v
                    }
                }
            }
        } else {
            batch.addBatch(this)
            for ((c, v) in pending) {
                batch[c] = v
            }
        }

        return true
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

        cache.forget(this)
        klass.removeFromCache(this)
    }

    internal fun hasInReferenceCache(ref: Column<*>): Boolean {
        return ref in referenceCache
    }

    internal fun <T> getReferenceFromCache(ref: Column<*>): T {
        return referenceCache[ref] as T
    }

    @Suppress("UNCHECKED_CAST")
    internal fun resolveColumnValue(column: Column<*>): Any? {
        @Suppress("UNCHECKED_CAST")
        val staged = stagedValueOrNone(column as Column<Any?>)
        return if (staged === NoStagedValue) _readValues?.getOrNull(column) else staged
    }

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
            else -> cache.discardDirty(this)
        }

        klass.removeFromCache(this)
        val reloaded = klass[id]
        cache.store(this)
        if (!cache.stageFreshRow(this, reloaded.readValues)) {
            _readValues = reloaded.readValues
        }
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
