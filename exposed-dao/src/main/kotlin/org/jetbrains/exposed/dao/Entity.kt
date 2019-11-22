package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class ColumnWithTransform<TColumn, TReal>(val column: Column<TColumn>, val toColumn: (TReal) -> TColumn, val toReal: (TColumn) -> TReal)

class View<out Target: Entity<*>> (val op : Op<Boolean>, val factory: EntityClass<*, Target>) : SizedIterable<Target> {
    override fun limit(n: Int, offset: Int): SizedIterable<Target> = factory.find(op).limit(n, offset)
    override fun count(): Int = factory.find(op).count()
    override fun empty(): Boolean = factory.find(op).empty()
    override fun forUpdate(): SizedIterable<Target> = factory.find(op).forUpdate()
    override fun notForUpdate(): SizedIterable<Target> = factory.find(op).notForUpdate()

    override operator fun iterator(): Iterator<Target> = factory.find(op).iterator()
    operator fun getValue(o: Any?, desc: KProperty<*>): SizedIterable<Target> = factory.find(op)
    override fun copy(): SizedIterable<Target> = View(op, factory)
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<Target> = factory.find(op).orderBy(*order)
}

@Suppress("UNCHECKED_CAST")
class InnerTableLink<SID:Comparable<SID>, Source: Entity<SID>, ID:Comparable<ID>, Target: Entity<ID>>(
        val table: Table,
        val target: EntityClass<ID, Target>,
        val sourceColumn: Column<EntityID<SID>>? = null,
        _targetColumn: Column<EntityID<ID>>? = null) : ReadWriteProperty<Source, SizedIterable<Target>> {
    init {
        _targetColumn?.let {
            requireNotNull(sourceColumn) { "Both source and target columns should be specified"}
            require(_targetColumn.referee?.table == target.table) {
                "Column $_targetColumn point to wrong table, expected ${target.table.tableName}"
            }
            require(_targetColumn.table == sourceColumn.table) {
                "Both source and target columns should be from the same table"
            }
        }
        sourceColumn?.let {
            requireNotNull(_targetColumn) { "Both source and target columns should be specified"}
        }
    }

    private val targetColumn = _targetColumn
            ?: table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<ID>>
            ?: error("Table does not reference target")

    private fun getSourceRefColumn(o: Source): Column<EntityID<SID>> {
        return sourceColumn ?: table.columns.singleOrNull { it.referee == o.klass.table.id } as? Column<EntityID<SID>> ?: error("Table does not reference source")
    }

    override operator fun getValue(o: Source, unused: KProperty<*>): SizedIterable<Target> {
        if (o.id._value == null) return emptySized()
        val sourceRefColumn = getSourceRefColumn(o)
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table)?: false
        val entityTables = if (alreadyInJoin) target.dependsOnTables else target.dependsOnTables.join(table, JoinType.INNER, target.table.id, targetColumn)

        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList())
            - sourceRefColumn).distinct() + sourceRefColumn

        val query = {target.wrapRows(entityTables.slice(columns).select{sourceRefColumn eq o.id})}
        return TransactionManager.current().entityCache.getOrPutReferrers(o.id, sourceRefColumn, query)
    }

    override fun setValue(o: Source, unused: KProperty<*>, value: SizedIterable<Target>) {
        val sourceRefColumn = getSourceRefColumn(o)

        val tx = TransactionManager.current()
        val entityCache = tx.entityCache
        entityCache.flush()
        val oldValue = getValue(o, unused)
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.clearReferrersCache()

        val targetIds = value.map { it.id }
        table.deleteWhere { (sourceRefColumn eq o.id) and (targetColumn notInList targetIds) }
        table.batchInsert(targetIds.filter { !existingIds.contains(it) }) { targetId ->
            this[sourceRefColumn] = o.id
            this[targetColumn] = targetId
        }

        // current entity updated
        EntityHook.registerChange(tx, EntityChange(o.klass, o.id, EntityChangeType.Updated))

        // linked entities updated
        val targetClass = (value.firstOrNull() ?: oldValue.firstOrNull())?.klass
        if (targetClass != null) {
            existingIds.plus(targetIds).forEach {
                EntityHook.registerChange(tx, EntityChange(targetClass, it, EntityChangeType.Updated))
            }
        }
    }
}

open class Entity<ID:Comparable<ID>>(val id: EntityID<ID>) {
    var klass: EntityClass<ID, Entity<ID>> by Delegates.notNull()
        internal set

    var db: Database by Delegates.notNull()
        internal set

    val writeValues = LinkedHashMap<Column<Any?>, Any?>()
    var _readValues: ResultRow? = null
    val readValues: ResultRow
    get() = _readValues ?: run {
        val table = klass.table
        _readValues = klass.searchQuery( Op.build {table.id eq id }).firstOrNull() ?: table.select { table.id eq id }.first()
        _readValues!!
    }

    /**
     * Updates entity fields from database.
     * Override function to refresh some additional state if any.
     *
     * @param flush whether pending entity changes should be flushed previously
     * @throws EntityNotFoundException if entity no longer exists in database
     */
    open fun refresh(flush: Boolean = false) {
        if (flush) flush() else writeValues.clear()

        klass.removeFromCache(this)
        val reloaded = klass[id]
        TransactionManager.current().entityCache.store(this)
        _readValues = reloaded.readValues
    }

    operator fun <REF:Comparable<REF>, RID:Comparable<RID>, T: Entity<RID>> Reference<REF, RID, T>.getValue(o: Entity<ID>, desc: KProperty<*>): T {
        val refValue = reference.getValue(o, desc)
        return when {
            refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> factory.findById(refValue.value as RID)
            else -> factory.findWithCacheCondition({ reference.referee!!.getValue(this, desc) == refValue }) { reference.referee<REF>()!! eq refValue }.singleOrNull()
        } ?: error("Cannot find ${factory.table.tableName} WHERE id=$refValue")
    }

    operator fun <REF:Comparable<REF>, RID:Comparable<RID>, T: Entity<RID>> Reference<REF, RID, T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        if (db != value.db) error("Can't link entities from different databases.")
        value.id.value // flush before creating reference on it
        val refValue = value.run { reference.referee<REF>()!!.getValue(this, desc) }
        reference.setValue(o, desc, refValue)
    }

    operator fun <REF:Comparable<REF>, RID:Comparable<RID>, T: Entity<RID>> OptionalReference<REF, RID, T>.getValue(o: Entity<ID>, desc: KProperty<*>): T? {
        val refValue = reference.getValue(o, desc)
        return when {
            refValue == null -> null
            refValue is EntityID<*> && reference.referee<REF>() == factory.table.id -> factory.findById(refValue.value as RID)
            else -> factory.findWithCacheCondition({ reference.referee!!.getValue(this, desc) == refValue }) { reference.referee<REF>()!! eq refValue }.singleOrNull()
        }
    }

    operator fun <REF:Comparable<REF>, RID:Comparable<RID>, T: Entity<RID>> OptionalReference<REF, RID, T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T?) {
        if (value != null && db != value.db) error("Can't link entities from different databases.")
        value?.id?.value // flush before creating reference on it
        val refValue = value?.run { reference.referee<REF>()!!.getValue(this, desc) }
        reference.setValue(o, desc, refValue)
    }
    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    @Suppress("UNCHECKED_CAST")
    fun <T, R:Any> Column<T>.lookupInReadValues(found: (T?) -> R?, notFound: () -> R?): R? =
        if (_readValues?.hasValue(this) == true)
            found(readValues[this])
        else
            notFound()

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
            if (referee != null) {
                val entityCache = TransactionManager.current().entityCache
                if (value is EntityID<*> && value.table == referee!!.table) value.value // flush

                listOfNotNull<Any>(value, currentValue).forEach {
                    entityCache.referrers[it]?.remove(this)
                }
                entityCache.removeTablesReferrers(listOf(referee!!.table))
            }
            writeValues[this as Column<Any?>] = value
        }
    }

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.getValue(o: Entity<ID>, desc: KProperty<*>): TReal =
            toReal(column.getValue(o, desc))

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.setValue(o: Entity<ID>, desc: KProperty<*>, value: TReal) {
        column.setValue(o, desc, toColumn(value))
    }

    infix fun <TID:Comparable<TID>, Target: Entity<TID>> EntityClass<TID, Target>.via(table: Table): InnerTableLink<ID, Entity<ID>, TID, Target> =
            InnerTableLink(table, this@via)

    fun <TID:Comparable<TID>, Target: Entity<TID>> EntityClass<TID, Target>.via(sourceColumn: Column<EntityID<ID>>, targetColumn: Column<EntityID<TID>>) =
            InnerTableLink(sourceColumn.table, this@via, sourceColumn, targetColumn)

    /**
     * Delete this entity.
     *
     * This will remove the entity from the database as well as the cache.
     */
    open fun delete(){
        klass.removeFromCache(this)
        val table = klass.table
        table.deleteWhere {table.id eq id}
        EntityHook.registerChange(TransactionManager.current(), EntityChange(klass, id, EntityChangeType.Removed))
    }

    open fun flush(batch: EntityBatchUpdate? = null): Boolean {
        if (writeValues.isNotEmpty()) {
            if (batch == null) {
                val table = klass.table
                // Store values before update to prevent flush inside UpdateStatement
                val _writeValues = writeValues.toMap()
                storeWrittenValues()
                table.update({table.id eq id}) {
                    for ((c, v) in _writeValues) {
                        it[c] = v
                    }
                }
            }
            else {
                batch.addBatch(id)
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
            if (klass.dependsOnColumns.any { it.table == klass.table && !_readValues!!.hasValue(it) } ) {
                _readValues = null
            }
        }
        // clear write values
        writeValues.clear()
    }
}
