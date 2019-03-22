package org.jetbrains.exposed.dao

import org.jetbrains.exposed.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.EntityBatchUpdate
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * @author max
 */
class EntityID<T:Comparable<T>>(id: T?, val table: IdTable<T>) : Comparable<EntityID<T>> {
    var _value: Any? = id
    val value: T get() {
        if (_value == null) {
            TransactionManager.current().entityCache.flushInserts(table)
            assert(_value != null) { "Entity must be inserted" }
        }

        @Suppress("UNCHECKED_CAST")
        return _value!! as T
    }

    override fun toString() = value.toString()

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID<*>) return false

        return other._value == _value && other.table == table
    }

    override fun compareTo(other: EntityID<T>): Int = value.compareTo(other.value)
}

private fun checkReference(reference: Column<*>, factoryTable: IdTable<*>) {
    val refColumn = reference.referee ?: error("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factoryTable != targetTable) {
        error("Column and factory point to different tables")
    }
}

class Reference<REF:Comparable<REF>, ID:Comparable<ID>, out Target : Entity<ID>> (val reference: Column<REF>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

class OptionalReference<REF:Comparable<REF>, ID:Comparable<ID>, out Target : Entity<ID>> (val reference: Column<REF?>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

internal class BackReference<ParentID:Comparable<ParentID>, out Parent:Entity<ParentID>, ChildID:Comparable<ChildID>, in Child:Entity<ChildID>, REF>
                    (reference: Column<REF>, factory: EntityClass<ParentID, Parent>) : ReadOnlyProperty<Child, Parent> {
    private val delegate = Referrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) = delegate.getValue(thisRef.apply { thisRef.id.value }, property).single() // flush entity before to don't miss newly created entities
}

class OptionalBackReference<ParentID:Comparable<ParentID>, out Parent:Entity<ParentID>, ChildID:Comparable<ChildID>, in Child:Entity<ChildID>, REF>
                    (reference: Column<REF?>, factory: EntityClass<ParentID, Parent>) : ReadOnlyProperty<Child, Parent?> {
    private val delegate = OptionalReferrers<ChildID, Child, ParentID, Parent, REF>(reference, factory, true)

    override operator fun getValue(thisRef: Child, property: KProperty<*>) = delegate.getValue(thisRef.apply { thisRef.id.value }, property).singleOrNull()  // flush entity before to don't miss newly created entities
}

class Referrers<ParentID:Comparable<ParentID>, in Parent:Entity<ParentID>, ChildID:Comparable<ChildID>, out Child:Entity<ChildID>, REF>
    (val reference: Column<REF>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = {factory.find{reference eq value }}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(thisRef.id, reference, query) else query()
    }
}

class OptionalReferrers<ParentID:Comparable<ParentID>, in Parent:Entity<ParentID>, ChildID:Comparable<ChildID>, out Child:Entity<ChildID>, REF>
(val reference: Column<REF?>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) : ReadOnlyProperty<Parent, SizedIterable<Child>> {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    override operator fun getValue(thisRef: Parent, property: KProperty<*>): SizedIterable<Child> {
        val value = thisRef.run { reference.referee<REF>()!!.lookup() }
        if (thisRef.id._value == null || value == null) return emptySized()

        val query = {factory.find{reference eq value }}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(thisRef.id, reference, query)  else query()
    }
}

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

        val entityCache = TransactionManager.current().entityCache
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
        EntityHook.registerChange(EntityChange(o.klass, o.id, EntityChangeType.Updated))

        // linked entities updated
        val targetClass = (value.firstOrNull() ?: oldValue.firstOrNull())?.klass
        if (targetClass != null) {
            existingIds.plus(targetIds).forEach {
                EntityHook.registerChange(EntityChange(targetClass, it, EntityChangeType.Updated))
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

    infix fun <TID:Comparable<TID>, Target:Entity<TID>> EntityClass<TID, Target>.via(table: Table): InnerTableLink<ID, Entity<ID>, TID, Target> =
            InnerTableLink(table, this@via)

    fun <TID:Comparable<TID>, Target:Entity<TID>> EntityClass<TID, Target>.via(sourceColumn: Column<EntityID<ID>>, targetColumn: Column<EntityID<TID>>) =
            InnerTableLink(sourceColumn.table, this@via,sourceColumn, targetColumn)

    /**
     * Delete this entity.
     *
     * This will remove the entity from the database as well as the cache.
     */
    open fun delete(){
        klass.removeFromCache(this)
        val table = klass.table
        table.deleteWhere {table.id eq id}
        EntityHook.registerChange(EntityChange(klass, id, EntityChangeType.Removed))
    }

    open fun flush(batch: EntityBatchUpdate? = null): Boolean {
        if (!writeValues.isEmpty()) {
            if (batch == null) {
                val table = klass.table
                table.update({table.id eq id}) {
                    for ((c, v) in writeValues) {
                        it[c] = v
                    }
                }
            }
            else {
                batch.addBatch(id)
                for ((c, v) in writeValues) {
                    batch[c] = v
                }
            }

            storeWrittenValues()
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

@Suppress("UNCHECKED_CAST")
class EntityCache {
    val data = HashMap<IdTable<*>, MutableMap<Any, Entity<*>>>()
    val inserts = HashMap<IdTable<*>, MutableList<Entity<*>>>()
    val referrers = HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>>()

    private fun getMap(f: EntityClass<*, *>) : MutableMap<Any,  Entity<*>> = getMap(f.table)

    private fun getMap(table: IdTable<*>) : MutableMap<Any, Entity<*>> = data.getOrPut(table) {
        HashMap()
    }

    fun <ID: Any, R: Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: ()-> SizedIterable<R>): SizedIterable<R> =
            referrers.getOrPut(sourceId){HashMap()}.getOrPut(key) {LazySizedCollection(refs())} as SizedIterable<R>

    fun <ID:Comparable<ID>, T: Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? = getMap(f)[id.value] as T? ?: inserts[f.table]?.firstOrNull { it.id == id } as? T

    fun <ID:Comparable<ID>, T: Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values as Collection<T>

    fun <ID:Comparable<ID>, T: Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f).put(o.id.value, o)
    }

    fun store(o: Entity<*>) {
        getMap(o.klass.table).put(o.id.value, o)
    }

    fun <ID:Comparable<ID>, T: Entity<ID>> remove(table: IdTable<ID>, o: T) {
        getMap(table).remove(o.id.value)
    }

    fun <ID:Comparable<ID>, T: Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
        inserts.getOrPut(f.table) { arrayListOf() }.add(o as Entity<*>)
    }

    fun flush() {
        flush(inserts.keys + data.keys)
    }

    fun flush(tables: Iterable<IdTable<*>>) {
        val insertedTables = inserts.keys

        for (t in SchemaUtils.sortTablesByReferences(tables)) {
            flushInserts(t as IdTable<*>)
        }

        for (t in tables) {
            data[t]?.let { map ->
                if (map.isNotEmpty()) {
                    val updatedEntities = HashSet<Entity<*>>()
                    val batch = EntityBatchUpdate(map.values.first().klass)
                    for ((_, entity) in map) {
                        if (entity.flush(batch)) {
                            if (entity.klass is ImmutableEntityClass<*,*>) {
                                throw IllegalStateException("Update on immutable entity ${entity.javaClass.simpleName} ${entity.id}")
                            }
                            updatedEntities.add(entity)
                        }
                    }
                    batch.execute(TransactionManager.current())
                    updatedEntities.forEach {
                        EntityHook.registerChange(EntityChange(it.klass, it.id, EntityChangeType.Updated))
                    }
                }
            }
        }

        if (insertedTables.isNotEmpty()) {
            removeTablesReferrers(insertedTables)
        }
    }

    internal fun removeTablesReferrers(insertedTables: Collection<Table>) {
        referrers.filterValues { it.any { it.key.table in insertedTables } }.map { it.key }.forEach {
            referrers.remove(it)
        }
    }

    internal fun flushInserts(table: IdTable<*>) {
        inserts.remove(table)?.let {
            var toFlush: List<Entity<*>> = it
            do {
                val partition = toFlush.partition {
                    it.writeValues.none {
                        val (key, value) = it
                        key.referee == table.id && value is EntityID<*> && value._value == null
                    }
                }
                toFlush = partition.first
                val ids = table.batchInsert(toFlush) { entry ->
                    for ((c, v) in entry.writeValues) {
                        this[c] = v
                    }
                }

                for ((entry, genValues) in toFlush.zip(ids)) {
                    if (entry.id._value == null) {
                        val id = genValues[table.id]
                        entry.id._value = id._value
                        entry.writeValues[entry.klass.table.id as Column<Any?>] = id
                    }
                    genValues.fieldIndex.keys.forEach { key ->
                        entry.writeValues[key as Column<Any?>] = genValues[key]
                    }

                    entry.storeWrittenValues()
                    store(entry)
                    EntityHook.registerChange(EntityChange(entry.klass, entry.id, EntityChangeType.Created))
                }
                toFlush = partition.second
            } while(toFlush.isNotEmpty())
        }
    }

    fun clearReferrersCache() {
        referrers.clear()
    }

    companion object {

        fun invalidateGlobalCaches(created: List<Entity<*>>) {
            created.asSequence().mapNotNull { it.klass as? ImmutableCachedEntityClass<*,*>}.distinct().forEach {
                it.expireCache()
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
abstract class EntityClass<ID : Comparable<ID>, out T: Entity<ID>>(val table: IdTable<ID>, entityType: Class<T>? = null) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>
    private val ctor = klass.kotlin.primaryConstructor!!

    operator fun get(id: EntityID<ID>): T = findById(id) ?: throw EntityNotFoundException(id, this)

    operator fun get(id: ID): T = get(EntityID(id, table))

    protected open fun warmCache(): EntityCache = TransactionManager.current().entityCache

    /**
     * Get an entity by its [id].
     *
     * @param id The id of the entity
     *
     * @return The entity that has this id or null if no entity was found.
     */
    fun findById(id: ID): T? = findById(EntityID(id, table))

    /**
     * Get an entity by its [id].
     *
     * @param id The id of the entity
     *
     * @return The entity that has this id or null if no entity was found.
     */
    open fun findById(id: EntityID<ID>): T? = testCache(id) ?: find{table.id eq id}.firstOrNull()

    /**
     * Reloads entity fields from database as new object.
     * @param flush whether pending entity changes should be flushed previously
     */
    fun reload(entity: Entity<ID>, flush: Boolean = false): T? {
        if (flush) entity.flush()
        removeFromCache(entity)
        return findById(entity.id)
    }

    internal open fun invalidateEntityInCache(o: Entity<ID>) {
        if (o.id._value != null && testCache(o.id) == null && TransactionManager.current().db == o.db) {
            get(o.id) // Check that entity is still exists in database
            warmCache().store(o)
        }
    }

    fun testCache(id: EntityID<ID>): T? = warmCache().find(this, id)

    fun testCache(cacheCheckCondition: T.()->Boolean): Sequence<T> = warmCache().findAll(this).asSequence().filter { it.cacheCheckCondition() }

    fun removeFromCache(entity: Entity<ID>) {
        val cache = warmCache()
        cache.remove(table, entity)
        cache.referrers.remove(entity.id)
        cache.removeTablesReferrers(listOf(table))
    }

    open fun forEntityIds(ids: List<EntityID<ID>>) : SizedIterable<T> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptySized()

        val cached = distinctIds.mapNotNull { testCache(it) }

        if (cached.size == distinctIds.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(Op.build { table.id inList distinctIds }))
    }

    fun forIds(ids: List<ID>) : SizedIterable<T> = forEntityIds(ids.map {EntityID (it, table)})

    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    @Deprecated("Transaction ", replaceWith = ReplaceWith("wrapRow(row)"), level = DeprecationLevel.ERROR)
    fun wrapRow (row: ResultRow, transaction: Transaction) : T = wrapRow(row)

    fun wrapRow(row: ResultRow) : T {
        val entity = wrap(row[table.id], row)
        if (entity._readValues == null)
            entity._readValues = row

        return entity
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
    fun find(op: SqlExpressionBuilder.()->Op<Boolean>): SizedIterable<T> = find(SqlExpressionBuilder.op())

    fun findWithCacheCondition(cacheCheckCondition: T.()->Boolean, op: SqlExpressionBuilder.()->Op<Boolean>): Sequence<T> {
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
    fun count(op: Op<Boolean>? = null): Int = with(TransactionManager.current()) {
        val query = table.slice(table.id.count())
        (if (op == null) query.selectAll() else query.select{op}).notForUpdate().first()[
            table.id.count()
        ]
    }

    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?) : T = ctor.call(entityId) as T

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
        val entityId = if (id == null && table.id.defaultValueFun != null)
            table.id.defaultValueFun!!()
        else
            EntityID(id, table)
        val prototype: T = createInstance(entityId, null)
        prototype.klass = this
        prototype.db = TransactionManager.current().db
        prototype._readValues = ResultRow.createAndFillDefaults(dependsOnColumns)
        if (entityId._value != null) {
            prototype.writeValues[table.id as Column<Any?>] = entityId
            warmCache().scheduleInsert(this, prototype)
        }
        prototype.init()
        if (entityId._value == null) {
            val readValues = prototype._readValues!!
            val writeValues = prototype.writeValues
            table.columns.filter { col ->
                col.defaultValueFun != null && col !in writeValues && readValues.hasValue(col)
            }.forEach { col ->
                writeValues[col as Column<Any?>] = readValues[col]
            }
            warmCache().scheduleInsert(this, prototype)
        }
        return prototype
    }

    inline fun view (op: SqlExpressionBuilder.() -> Op<Boolean>)  = View(SqlExpressionBuilder.op(), this)

    private val refDefinitions = HashMap<Pair<Column<*>, KClass<*>>, Any>()

    private inline fun <reified R: Any> registerRefRule(column: Column<*>, ref:()-> R): R =
            refDefinitions.getOrPut(column to R::class, ref) as R

    infix fun <REF:Comparable<REF>> referencedOn(column: Column<REF>) = registerRefRule(column) { Reference(column, this) }

    infix fun <REF:Comparable<REF>> optionalReferencedOn(column: Column<REF?>) = registerRefRule(column) { OptionalReference(column, this) }

    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF:Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(column: Column<REF>)
            : ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    @JvmName("backReferencedOnOpt")
    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF:Comparable<REF>> EntityClass<TargetID, Target>.backReferencedOn(column: Column<REF?>)
            : ReadOnlyProperty<Entity<ID>, Target> = registerRefRule(column) { BackReference(column, this) }

    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF:Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(column: Column<REF>)
            = registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column as Column<REF?>, this) }

    @JvmName("optionalBackReferencedOnOpt")
    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF:Comparable<REF>> EntityClass<TargetID, Target>.optionalBackReferencedOn(column: Column<REF?>)
            = registerRefRule(column) { OptionalBackReference<TargetID, Target, ID, Entity<ID>, REF>(column, this) }

    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF: Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(column: Column<REF>)
            = registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, false) }

    fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF: Comparable<REF>> EntityClass<TargetID, Target>.referrersOn(column: Column<REF>, cache: Boolean)
            = registerRefRule(column) { Referrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    infix fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF: Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(column : Column<REF?>)
            = registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, false) }

    fun <TargetID: Comparable<TargetID>, Target:Entity<TargetID>, REF: Comparable<REF>> EntityClass<TargetID, Target>.optionalReferrersOn(column: Column<REF?>, cache: Boolean = false) =
            registerRefRule(column) { OptionalReferrers<ID, Entity<ID>, TargetID, Target, REF>(column, this, cache) }

    fun<TColumn: Any?,TReal: Any?> Column<TColumn>.transform(toColumn: (TReal) -> TColumn, toReal: (TColumn) -> TReal): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal)

    private fun Query.setForUpdateStatus(): Query = if (this@EntityClass is ImmutableEntityClass<*,*>) this.notForUpdate() else this

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun <SID> warmUpOptReferences(references: List<SID>, refColumn: Column<SID?>, forUpdate: Boolean? = null): List<T>
            = warmUpReferences(references, refColumn as Column<SID>, forUpdate)

    fun <SID> warmUpReferences(references: List<SID>, refColumn: Column<SID>, forUpdate: Boolean? = null): List<T> {
        val parentTable = refColumn.referee?.table as? IdTable<*>
        requireNotNull(parentTable) { "RefColumn should have reference to IdTable" }
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct()
        val cache = TransactionManager.current().entityCache
        if (refColumn.columnType is EntityIDColumnType<*>) {
            refColumn as Column<EntityID<*>>
            distinctRefIds as List<EntityID<ID>>
            val toLoad = distinctRefIds.filter {
                 cache.referrers[it]?.containsKey(refColumn)?.not() ?: true
            }
            if (toLoad.isNotEmpty()) {
                val findQuery = find { refColumn inList toLoad }
                val entities = when(forUpdate) {
                    true -> findQuery.forUpdate()
                    false -> findQuery.notForUpdate()
                    else -> findQuery
                }.toList()

                val result = entities.groupBy { it.readValues[refColumn] }

                distinctRefIds.forEach { id ->
                    cache.getOrPutReferrers(id, refColumn) { result[id]?.let { SizedCollection(it) } ?: emptySized<T>() }
                }
            }

            return distinctRefIds.flatMap { cache.referrers[it]?.get(refColumn)?.toList().orEmpty() } as List<T>
        } else {
            val baseQuery = searchQuery(Op.build{ refColumn inList distinctRefIds })
            val finalQuery = if (parentTable.id in baseQuery.set.fields)
                baseQuery
            else {
                baseQuery.adjustSlice{ slice(this.fields + parentTable.id) }.
                        adjustColumnSet { innerJoin(parentTable, { refColumn }, { refColumn.referee!! }) }
            }

            val findQuery = wrapRows(finalQuery)
            val entities = when(forUpdate) {
                true -> findQuery.forUpdate()
                false -> findQuery.notForUpdate()
                else -> findQuery
            }.toList().distinct()

            entities.groupBy { it.readValues[parentTable.id] }.forEach { (id, values) ->
                cache.getOrPutReferrers(id, refColumn) { SizedCollection(values) }
            }
            return entities
        }
    }

    fun warmUpLinkedReferences(references: List<EntityID<*>>, linkTable: Table, forUpdate: Boolean? = null): List<T> {
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct()
        val sourceRefColumn = linkTable.columns.singleOrNull { it.referee == references.first().table.id } as? Column<EntityID<*>> ?: error("Can't detect source reference column")
        val targetRefColumn = linkTable.columns.singleOrNull {it.referee == table.id}  as? Column<EntityID<*>>?: error("Can't detect target reference column")

        val transaction = TransactionManager.current()

        val inCache = transaction.entityCache.referrers.filter { it.key in distinctRefIds && sourceRefColumn in it.value }.mapValues { it.value[sourceRefColumn]!! }
        val loaded = (distinctRefIds - inCache.keys).takeIf { it.isNotEmpty() }?.let { idsToLoad ->
            val alreadyInJoin = (dependsOnTables as? Join)?.alreadyInJoin(linkTable) ?: false
            val entityTables = if (alreadyInJoin) dependsOnTables else dependsOnTables.join(linkTable, JoinType.INNER, targetRefColumn, table.id)

            val columns = (dependsOnColumns + (if (!alreadyInJoin) linkTable.columns else emptyList())
                    - sourceRefColumn).distinct() + sourceRefColumn

            val query = entityTables.slice(columns).select { sourceRefColumn inList idsToLoad }
            val entitiesWithRefs = when(forUpdate) {
                true -> query.forUpdate()
                false -> query.notForUpdate()
                else -> query
            }.map { it[sourceRefColumn] to wrapRow(it) }

            val groupedBySourceId = entitiesWithRefs.groupBy { it.first }.mapValues { it.value.map { it.second } }

            idsToLoad.forEach {
                transaction.entityCache.getOrPutReferrers(it, sourceRefColumn) { SizedCollection(groupedBySourceId[it] ?: emptyList()) }
            }
            entitiesWithRefs.map { it.second }
        }
        return inCache.values.flatMap { it.toList() as List<T> } + loaded.orEmpty()
    }

    fun <ID : Comparable<ID>, T: Entity<ID>> isAssignableTo(entityClass: EntityClass<ID, T>) = entityClass.klass.isAssignableFrom(klass)
}

private fun <SRC: Entity<*>> getReferenceObjectFromDelegatedProperty(entity: SRC, property: KProperty1<SRC, Any?>) : Any? {
    property.isAccessible   = true
    return property.getDelegate(entity)
}

private fun <SRC: Entity<*>> filterRelationsForEntity(entity: SRC, relations: Array<out KProperty1<out Entity<*>, Any?>>): Collection<KProperty1<SRC, Any?>> {
    val validMembers = entity::class.memberProperties
    return validMembers.filter { it in relations } as Collection<KProperty1<SRC, Any?>>
}

private fun <ID: Comparable<ID>> List<Entity<ID>>.preloadRelations(vararg relations: KProperty1<out Entity<*>, Any?>,
                                                                   nodesVisited: MutableSet<EntityClass<*, *>> = mutableSetOf())  {
    val entity              = this.firstOrNull() ?: return
    if(nodesVisited.contains(entity.klass)) {
        return
    } else {
        nodesVisited.add(entity.klass)
    }

    val directRelations = filterRelationsForEntity(entity, relations)
    directRelations.forEach {
        when(val refObject = getReferenceObjectFromDelegatedProperty(entity, it)) {
            is Reference<*, *, *> -> {
                (refObject as Reference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.lookup() } }
                    refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds }.toList()
                }
            }
            is OptionalReference<*, *, *> -> {
                (refObject as OptionalReference<Comparable<Comparable<*>>, *, Entity<*>>).reference.let { refColumn ->
                    val refIds = this.mapNotNull { it.run { refColumn.lookup() } }
                    refObject.factory.find { refColumn.referee<Comparable<Comparable<*>>>()!! inList refIds }.toList()
                }
            }
            is Referrers<*, *, *, *, *> -> {
                (refObject as Referrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.map { it.run { refColumn.referee<Any>()!!.lookup() } }
                    refObject.factory.warmUpReferences(refIds, refColumn)
                }
            }
            is OptionalReferrers<*,*,*,*,*> -> {
                (refObject as OptionalReferrers<ID, Entity<ID>, *, Entity<*>, Any>).reference.let { refColumn ->
                    val refIds = this.mapNotNull { it.run { refColumn.referee<Any?>()!!.lookup() } }
                    refObject.factory.warmUpOptReferences(refIds, refColumn)
                }
            }
            is InnerTableLink<*,*,*,*> -> {
                refObject.target.warmUpLinkedReferences(this.map{ it.id }, refObject.table)
            }
            else -> error("Relation delegate has an unknown type")
        }
    }

    if(directRelations.isNotEmpty() && relations.size != directRelations.size) {
        val remainingRelations      = relations.toList() - directRelations
        directRelations.map { relationProperty ->
            val relationsToLoad = this.flatMap {
                when(val relation = (relationProperty as KProperty1<Entity<*>, *>).get(it)) {
                    is SizedIterable<*> -> relation.toList()
                    is Entity<*>        -> listOf(relation)
                    null                -> listOf()
                    else                -> error("Unrecognised loaded relation")
                } as List<Entity<Int>>
            }.groupBy { it::class }

            relationsToLoad.forEach { _, entities ->
                entities.preloadRelations(*remainingRelations.toTypedArray() as Array<out KProperty1<Entity<*>, Any?>>, nodesVisited = nodesVisited)
            }
        }
    }
}


fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>, REF : Entity<*>> List<SRC>
        .with(vararg relations: KProperty1<out REF, Any?>) : List<SRC>
        = this.apply {
    preloadRelations(*relations)
}

fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>> SRC.load(vararg relations: KProperty1<out Entity<*>, Any?>) : SRC {

    listOf(this).with(*relations)
    return this
}

fun <SRCID : Comparable<SRCID>, SRC: Entity<SRCID>> SizedIterable<SRC>.with(vararg relations: KProperty1<out Entity<*>, Any?>) : List<SRC>
        = this.toList().with(*relations)



abstract class ImmutableEntityClass<ID:Comparable<ID>, out T: Entity<ID>>(table: IdTable<ID>, entityType: Class<T>? = null) : EntityClass<ID, T>(table, entityType) {
    open fun <T> forceUpdateEntity(entity: Entity<ID>, column: Column<T>, value: T) {
        table.update({ table.id eq entity.id }) {
            it[column] = value
        }
    }
}

abstract class ImmutableCachedEntityClass<ID:Comparable<ID>, out T: Entity<ID>>(table: IdTable<ID>, entityType: Class<T>? = null) : ImmutableEntityClass<ID, T>(table, entityType) {

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
                cachedValues != null -> {} // already loaded in another transaction
                tr.getUserData(cacheLoadingState) != null -> {
                    return transactionCache // prevent recursive call to warmCache() in .all()
                }
                else -> {
                    tr.putUserData(cacheLoadingState, this)
                    super.all().toList()  /* force iteration to initialize lazy collection */
                    _cachedValues[db] = transactionCache.data[table] ?: mutableMapOf()
                    tr.removeUserData(cacheLoadingState)
                }
            }
        }
        transactionCache.data[table] = _cachedValues[db]!!
        return transactionCache
    }

    override fun all(): SizedIterable<T> = SizedCollection(warmCache().findAll(this))

    @Synchronized fun expireCache() {
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
