package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.EntityBatchUpdate
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*
import kotlin.properties.Delegates
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * @author max
 */
class EntityID<T:Any>(id: T?, val table: IdTable<T>) {
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
}

private fun <T:EntityID<*>?>checkReference(reference: Column<T>, factoryTable: IdTable<*>) {
    val refColumn = reference.referee ?: error("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factoryTable != targetTable) {
        error("Column and factory point to different tables")
    }
}

class Reference<ID:Any, out Target : Entity<ID>> (val reference: Column<EntityID<ID>>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

class OptionalReference<ID:Any, out Target: Entity<ID>> (val reference: Column<EntityID<ID>?>, val factory: EntityClass<ID, Target>) {
    init {
        checkReference(reference, factory.table)
    }
}

class BackReference<ParentID:Any, out Parent:Entity<ParentID>, ChildID:Any, in Child:Entity<ChildID>>
                    (reference: Column<EntityID<ChildID>>, factory: EntityClass<ParentID, Parent>) {
    private val delegate = Referrers(reference, factory, true)

    operator fun getValue(o: Child, desc: KProperty<*>) = delegate.getValue(o.apply { o.id.value }, desc).single() // flush entity before to don't miss newly created entities
}

class OptionalBackReference<ParentID:Any, out Parent:Entity<ParentID>, ChildID:Any, in Child:Entity<ChildID>>
                    (reference: Column<EntityID<ChildID>?>, factory: EntityClass<ParentID, Parent>) {
    private val delegate = OptionalReferrers(reference, factory, true)

    operator fun getValue(o: Child, desc: KProperty<*>) = delegate.getValue(o.apply { o.id.value }, desc).singleOrNull()  // flush entity before to don't miss newly created entities
}

class Referrers<ParentID:Any, in Parent:Entity<ParentID>, ChildID:Any, out Child:Entity<ChildID>>
    (val reference: Column<EntityID<ParentID>>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    operator fun getValue(o: Parent, desc: KProperty<*>): SizedIterable<Child> {
        if (o.id._value == null) return emptySized()
        val query = {factory.find{reference eq o.id}}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(o.id, reference, query) else query()
    }
}

class OptionalReferrers<ParentID:Any, in Parent:Entity<ParentID>, ChildID:Any, out Child:Entity<ChildID>>
(val reference: Column<EntityID<ParentID>?>, val factory: EntityClass<ChildID, Child>, val cache: Boolean) {
    init {
        reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    operator fun getValue(o: Parent, desc: KProperty<*>): SizedIterable<Child> {
        if (o.id._value == null) return emptySized()
        val query = {factory.find{reference eq o.id}}
        return if (cache) TransactionManager.current().entityCache.getOrPutReferrers(o.id, reference, query)  else query()
    }
}

open class ColumnWithTransform<TColumn, TReal>(val column: Column<TColumn>, val toColumn: (TReal) -> TColumn, val toReal: (TColumn) -> TReal)

class View<out Target: Entity<*>> (val op : Op<Boolean>, val factory: EntityClass<*, Target>) : SizedIterable<Target> {
    override fun limit(n: Int, offset: Int): SizedIterable<Target> = factory.find(op).limit(n, offset)
    override fun count(): Int = factory.find(op).count()
    override fun empty(): Boolean = factory.find(op).empty()
    override fun forUpdate(): SizedIterable<Target> = factory.find(op).forUpdate()
    override fun notForUpdate(): SizedIterable<Target> = factory.find(op).notForUpdate()

    operator override fun iterator(): Iterator<Target> = factory.find(op).iterator()
    operator fun getValue(o: Any?, desc: KProperty<*>): SizedIterable<Target> = factory.find(op)
}

@Suppress("UNCHECKED_CAST")
class InnerTableLink<ID:Any, Target: Entity<ID>>(val table: Table,
                                     val target: EntityClass<ID, Target>) {
    private fun getSourceRefColumn(o: Entity<*>): Column<EntityID<*>> {
        val sourceRefColumn = table.columns.singleOrNull { it.referee == o.klass.table.id } as? Column<EntityID<*>> ?: error("Table does not reference source")
        return sourceRefColumn
    }

    private fun getTargetRefColumn(): Column<EntityID<*>> {
        val sourceRefColumn = table.columns.singleOrNull { it.referee == target.table.id } as? Column<EntityID<*>> ?: error("Table does not reference target")
        return sourceRefColumn
    }

    operator fun getValue(o: Entity<*>, desc: KProperty<*>): SizedIterable<Target> {
        if (o.id._value == null) return emptySized()
        val sourceRefColumn = getSourceRefColumn(o)
        val alreadyInJoin = (target.dependsOnTables as? Join)?.alreadyInJoin(table)?: false
        val entityTables = if (alreadyInJoin) target.dependsOnTables else target.dependsOnTables.join(table, JoinType.INNER, target.table.id, getTargetRefColumn())

        val columns = (target.dependsOnColumns + (if (!alreadyInJoin) table.columns else emptyList())
            - sourceRefColumn).distinct() + sourceRefColumn

        val query = {target.wrapRows(entityTables.slice(columns).select{sourceRefColumn eq o.id})}
        return TransactionManager.current().entityCache.getOrPutReferrers(o.id, sourceRefColumn, query)
    }

    operator fun<SrcID : Any> setValue(o: Entity<SrcID>, desc: KProperty<*>, value: SizedIterable<Target>) {
        val sourceRefColumn = getSourceRefColumn(o)
        val targetRefColumn = getTargetRefColumn()

        val entityCache = TransactionManager.current().entityCache
        entityCache.flush()
        val oldValue = getValue(o, desc)
        val existingIds = oldValue.map { it.id }.toSet()
        entityCache.clearReferrersCache()

        val targetIds = value.map { it.id }
        table.deleteWhere { (sourceRefColumn eq o.id) and (targetRefColumn notInList targetIds) }
        table.batchInsert(targetIds.filter { !existingIds.contains(it) }) { targetId ->
            this[sourceRefColumn] = o.id
            this[targetRefColumn] = targetId
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

open class Entity<ID:Any>(val id: EntityID<ID>) {
    var klass: EntityClass<ID, Entity<ID>> by Delegates.notNull()

    val writeValues = LinkedHashMap<Column<Any?>, Any?>()
    var _readValues: ResultRow? = null
    val readValues: ResultRow
    get() = _readValues ?: run {
        val table = klass.table
        _readValues = klass.searchQuery( Op.build {table.id eq id }).firstOrNull() ?: table.select { table.id eq id }.first()
        _readValues!!
    }

    operator fun <RID:Any, T: Entity<RID>> Reference<RID, T>.getValue(o: Entity<ID>, desc: KProperty<*>): T {
        val id = reference.getValue(o, desc)
        return factory.findById(id) ?: error("Cannot find ${factory.table.tableName} WHERE id=$id")
    }

    operator fun <RID:Any, T: Entity<RID>> Reference<RID, T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        value.id.value // flush before creating reference on it
        reference.setValue(o, desc, value.id)
    }

    operator fun <RID:Any, T: Entity<RID>> OptionalReference<RID, T>.getValue(o: Entity<ID>, desc: KProperty<*>): T? =
            reference.getValue(o, desc)?.let{factory.findById(it)}

    operator fun <RID:Any, T: Entity<RID>> OptionalReference<RID, T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T?) {
        value?.id?.value // flush before creating reference on it
        reference.setValue(o, desc, value?.id)
    }

    operator fun <T> Column<T>.getValue(o: Entity<ID>, desc: KProperty<*>): T = lookup()

    @Suppress("UNCHECKED_CAST")
    fun <T, R:Any> Column<T>.lookupInReadValues(found: (T?) -> R?, notFound: () -> R?): R? =
        if (_readValues?.hasValue(this) ?: false)
            found(readValues[this])
        else
            notFound()

    @Suppress("UNCHECKED_CAST")
    fun <T:Any?> Column<T>.lookup(): T = when {
        writeValues.containsKey(this as Column<out Any?>) -> writeValues[this as Column<out Any?>] as T
        id._value == null && _readValues?.hasValue(this)?.not() ?: true -> defaultValueFun?.invoke() as T
        else -> readValues[this]
    }

    operator fun <T> Column<T>.setValue(o: Entity<ID>, desc: KProperty<*>, value: T) {
        klass.invalidateEntityInCache(o)
        val currentValue = _readValues?.tryGet(this)
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

    infix fun <ID:Any, Target:Entity<ID>> EntityClass<ID, Target>.via(table: Table): InnerTableLink<ID, Target> =
            InnerTableLink(table, this@via)

    fun <T: Entity<*>> s(c: EntityClass<T, *>): EntityClass<T, *> = c

    open fun delete(){
        klass.removeFromCache(this)
        val table = klass.table
        table.deleteWhere{table.id eq id}
        EntityHook.registerChange(EntityChange(klass, id, EntityChangeType.Removed))
    }

    open fun flush(batch: EntityBatchUpdate<ID>? = null): Boolean {
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
                _readValues!![c] = v?.let { c.columnType.valueFromDB(c.columnType.valueToDB(it)!!)}
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
    val data = HashMap<IdTable<*>, MutableMap<Any, Entity<Any>>>()
    val inserts = HashMap<IdTable<*>, MutableList<Entity<Any>>>()
    val referrers = HashMap<EntityID<*>, MutableMap<Column<*>, SizedIterable<*>>>()

    private fun <ID:Any, T: Entity<ID>> getMap(f: EntityClass<ID, T>) : MutableMap<ID, T> = getMap(f.table)

    private fun <ID:Any, T: Entity<ID>> getMap(table: IdTable<ID>) : MutableMap<ID, T> = data.getOrPut(table, {
        HashMap()
    }) as MutableMap<ID, T>

    fun <ID: Any, R: Entity<ID>> getOrPutReferrers(sourceId: EntityID<*>, key: Column<*>, refs: ()-> SizedIterable<R>): SizedIterable<R> =
            referrers.getOrPut(sourceId, {HashMap()}).getOrPut(key, {LazySizedCollection(refs())}) as SizedIterable<R>

    fun <ID:Any, T: Entity<ID>> find(f: EntityClass<ID, T>, id: EntityID<ID>): T? = getMap(f)[id.value] ?: inserts[f.table]?.firstOrNull { it.id == id } as? T

    fun <ID:Any, T: Entity<ID>> findAll(f: EntityClass<ID, T>): Collection<T> = getMap(f).values

    fun <ID:Any, T: Entity<ID>> store(f: EntityClass<ID, T>, o: T) {
        getMap(f).put(o.id.value, o)
    }

    fun <ID:Any, T: Entity<ID>> store(o: T) {
        getMap(o.klass.table).put(o.id.value, o)
    }

    fun <ID:Any, T: Entity<ID>> remove(table: IdTable<ID>, o: T) {
        getMap<ID, T>(table).remove(o.id.value)
    }

    fun <ID:Any, T: Entity<ID>> scheduleInsert(f: EntityClass<ID, T>, o: T) {
        inserts.getOrPut(f.table) { arrayListOf() }.add(o as Entity<Any>)
    }

    fun flush() {
        flush(inserts.keys + data.keys)
    }

    fun flush(tables: Iterable<IdTable<*>>) {

        val insertedTables = inserts.keys

        for (t in sortTablesByReferences(tables)) {
            flushInserts(t as IdTable<*>)
        }

        for (t in tables) {
            data[t]?.let { map ->
                if (map.isNotEmpty()) {
                    val updatedEntities = HashSet<Entity<*>>()
                    val batch = EntityBatchUpdate(map.values.first().klass)
                    for ((i, entity) in map) {
                        if (entity.flush(batch)) {
                            if (entity.klass is ImmutableEntityClass<*,*>) {
                                throw IllegalStateException("Update on immutable entity ${entity.javaClass.simpleName} ${entity.id}")
                            }
                            updatedEntities.add(entity)
                        }
                    }
                    batch.execute(TransactionManager.current())
                    updatedEntities.forEach {
                        EntityHook.registerChange(EntityChange(it.klass as EntityClass<Any, Entity<Any>>, it.id as EntityID<Any>, EntityChangeType.Updated))
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
            var toFlush: List<Entity<Any>> = it
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
                    if (entry.id._value != null) continue
                    val id = genValues[table.id]!!
                    entry.id._value = (table.id.columnType as EntityIDColumnType<*>).idColumn.columnType.valueFromDB(when (id) {
                        is EntityID<*> -> id._value!!
                        else -> id
                    })
                    entry.writeValues[entry.klass.table.id as Column<Any?>] = id
                    genValues.forEach {
                        entry.writeValues[it.key as Column<Any?>] = it.value
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
            created.map { it.klass }.filterIsInstance<ImmutableCachedEntityClass<*,*>>().distinct().forEach {
                it.expireCache()
            }
        }

        fun sortTablesByReferences(tables: Iterable<Table>) = addDependencies(tables).toCollection(arrayListOf()).run {
            if(this.count() <= 1) return this
            val canBeReferenced = arrayListOf<Table>()
            do {
                val (movable, others) = partition {
                    it.columns.all { it.referee == null || canBeReferenced.contains(it.referee!!.table) || it.referee!!.table == it.table}
                }
                canBeReferenced.addAll(movable)
                this.removeAll(movable)
            } while (others.isNotEmpty() && movable.isNotEmpty())
            canBeReferenced.addAll(this)
            canBeReferenced
        }

        fun addDependencies(tables: Iterable<Table>): Iterable<Table> {
            val workset = HashSet<Table>()

            fun checkTable(table: Table) {
                if (workset.add(table)) {
                    for (c in table.columns) {
                        (c.referee?.table as? IdTable<*>)?.let {
                            checkTable(it)
                        }
                    }
                }
            }

            for (t in tables) checkTable(t)

            return workset
        }
    }
}

@Suppress("UNCHECKED_CAST")
abstract class EntityClass<ID : Any, out T: Entity<ID>>(val table: IdTable<ID>, entityType: Class<T>? = null) {
    internal val klass: Class<*> = entityType ?: javaClass.enclosingClass as Class<T>
    private val ctor = klass.constructors[0]

    operator fun get(id: EntityID<ID>): T = findById(id) ?: error("Entity id $id not found in database")

    operator fun get(id: ID): T = findById(id) ?: error("Entity id $id not found in database")

    open protected fun warmCache(): EntityCache = TransactionManager.current().entityCache

    fun findById(id: ID): T? = findById(EntityID(id, table))

    open fun findById(id: EntityID<ID>): T? = testCache(id) ?: find{table.id eq id}.firstOrNull()

    fun reload(entity: Entity<ID>): T? {
        removeFromCache(entity)
        return find { table.id eq entity.id }.firstOrNull()
    }

    internal open fun invalidateEntityInCache(o: Entity<ID>) {
        if (o.id._value != null && testCache(o.id) == null) {
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

        val toLoad = distinctIds - cached.map { it.id }
        val loaded = wrapRows(searchQuery(Op.build { table.id inList (toLoad) }))
        if (cached.isEmpty()) {
            return loaded
        } else {
            return SizedCollection(cached + loaded.toList())
        }
    }

    fun forIds(ids: List<ID>) : SizedIterable<T> = forEntityIds(ids.map {EntityID (it, table)})

    fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> = rows mapLazy {
        wrapRow(it)
    }

    @Deprecated("Transaction ", replaceWith = ReplaceWith("wrapRow(row)"), level = DeprecationLevel.WARNING)
    fun wrapRow (row: ResultRow, transaction: Transaction) : T = wrapRow(row)

    fun wrapRow(row: ResultRow) : T {
        val entity = wrap(row[table.id], row)
        if (entity._readValues == null)
            entity._readValues = row

        return entity
    }

    open fun all(): SizedIterable<T> = wrapRows(table.selectAll().notForUpdate())

    fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    fun find(op: SqlExpressionBuilder.()->Op<Boolean>): SizedIterable<T> {
        warmCache()
        return find(SqlExpressionBuilder.op())
    }

    fun findWithCacheCondition(cacheCheckCondition: T.()->Boolean, op: SqlExpressionBuilder.()->Op<Boolean>): Sequence<T> {
        val cached = testCache(cacheCheckCondition)
        return if (cached.any()) cached else find(op).asSequence()
    }

    open val dependsOnTables: ColumnSet get() = table
    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    open fun searchQuery(op: Op<Boolean>): Query =
            dependsOnTables.slice(dependsOnColumns).select { op }.setForUpdateStatus()

    fun count(op: Op<Boolean>? = null): Int = with(TransactionManager.current()) {
        val query = table.slice(table.id.count())
        (if (op == null) query.selectAll() else query.select{op}).notForUpdate().first()[
            table.id.count()
        ]
    }

    protected open fun createInstance(entityId: EntityID<ID>, row: ResultRow?) : T = ctor.newInstance(entityId) as T

    fun wrap(id: EntityID<ID>, row: ResultRow?): T {
        return TransactionManager.current().entityCache.find(this, id) ?: createInstance(id, row).also { new ->
            new.klass = this
            warmCache().store(this, new)
        }
    }

    open fun new(init: T.() -> Unit) = new(null, init)

    open fun new(id: ID?, init: T.() -> Unit): T {
        val entityId = EntityID(id, table)
        val prototype: T = createInstance(entityId, null)
        prototype.klass = this
        prototype._readValues = ResultRow.create(dependsOnColumns)
        if (id != null) {
            prototype.writeValues.put(table.id as Column<Any?>, entityId)
            warmCache().scheduleInsert(this, prototype)
        }
        prototype.init()
        if (id == null) {
            warmCache().scheduleInsert(this, prototype)
        }
        return prototype
    }

    inline fun view (op: SqlExpressionBuilder.() -> Op<Boolean>)  = View(SqlExpressionBuilder.op(), this)

    private val refDefinitions = HashMap<Pair<Column<*>, KClass<*>>, Any>()

    inline private fun <reified R: Any> registerRefRule(column: Column<*>, ref:()-> R): R =
            refDefinitions.getOrPut(column to R::class, ref) as R

    infix fun referencedOn(column: Column<EntityID<ID>>) = registerRefRule(column) { Reference(column, this) }

    infix fun optionalReferencedOn(column: Column<EntityID<ID>?>) = registerRefRule(column) { OptionalReference(column, this) }

    infix fun <TargetID: Any, Target:Entity<TargetID>> EntityClass<TargetID, Target>.backReferencedOn(column: Column<EntityID<ID>>)
            = registerRefRule(column) { BackReference(column, this) }

    @JvmName("backReferencedOnOpt")
    infix fun <TargetID: Any, Target:Entity<TargetID>> EntityClass<TargetID, Target>.backReferencedOn(column: Column<EntityID<ID>?>)
            = registerRefRule(column) { BackReference(column as Column<EntityID<ID>>, this) }

    infix fun <TargetID: Any, Target:Entity<TargetID>> EntityClass<TargetID, Target>.optionalBackReferencedOn(column: Column<EntityID<ID>>)
            = registerRefRule(column) { OptionalBackReference(column as Column<EntityID<ID>?>, this) }

    @JvmName("optionalBackReferencedOnOpt")
    infix fun <TargetID: Any, Target:Entity<TargetID>> EntityClass<TargetID, Target>.optionalBackReferencedOn(column: Column<EntityID<ID>?>)
            = registerRefRule(column) { OptionalBackReference(column, this) }

    infix fun <TargetID: Any> referrersOn(column: Column<EntityID<TargetID>>) = referrersOn(column, false)

    fun <TargetID: Any> referrersOn(column: Column<EntityID<TargetID>>, cache: Boolean) = registerRefRule(column) { Referrers(column, this, cache) }

    fun <TargetID: Any> optionalReferrersOn(column: Column<EntityID<TargetID>?>, cache: Boolean = false) =  registerRefRule(column) { OptionalReferrers(column, this, cache) }

    fun<TColumn: Any?,TReal: Any?> Column<TColumn>.transform(toColumn: (TReal) -> TColumn, toReal: (TColumn) -> TReal): ColumnWithTransform<TColumn, TReal> = ColumnWithTransform(this, toColumn, toReal)

    private fun Query.setForUpdateStatus(): Query = if (this@EntityClass is ImmutableEntityClass<*,*>) this.notForUpdate() else this

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun warmUpOptReferences(references: List<EntityID<ID>>, refColumn: Column<EntityID<ID>?>): List<T> = warmUpReferences(references, refColumn as Column<EntityID<ID>>)

    fun warmUpReferences(references: List<EntityID<ID>>, refColumn: Column<EntityID<ID>>): List<T> {
        if (references.isEmpty()) return emptyList()
        val distinctRefIds = references.distinct()
        checkReference(refColumn, references.first().table)
        val entities = find { refColumn inList distinctRefIds }.toList()
        val groupedBySourceId = entities.groupBy { it.readValues[refColumn] }
        distinctRefIds.forEach {
            TransactionManager.current().entityCache.getOrPutReferrers(it, refColumn, { SizedCollection(groupedBySourceId[it]?:emptyList()) })
        }
        return entities
    }

    fun warmUpLinkedReferences(references: List<EntityID<*>>, linkTable: Table): List<T> {
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

            val entitiesWithRefs = entityTables.slice(columns).select { sourceRefColumn inList idsToLoad }.map { it[sourceRefColumn] to wrapRow(it) }

            val groupedBySourceId = entitiesWithRefs.groupBy { it.first }.mapValues { it.value.map { it.second } }

            idsToLoad.forEach {
                transaction.entityCache.getOrPutReferrers(it, sourceRefColumn, { SizedCollection(groupedBySourceId[it] ?: emptyList()) })
            }
            entitiesWithRefs.map { it.second }
        }
        return inCache.values.flatMap { it.toList() as List<T> } + loaded.orEmpty()
    }

    fun <ID : Any, T: Entity<ID>> isAssignableTo(entityClass: EntityClass<ID, T>) = entityClass.klass.isAssignableFrom(klass)
}

abstract class ImmutableEntityClass<ID:Any, out T: Entity<ID>>(table: IdTable<ID>, entityType: Class<T>? = null) : EntityClass<ID, T>(table, entityType) {
    open fun <T> forceUpdateEntity(entity: Entity<ID>, column: Column<T>, value: T) {
        table.update({ table.id eq entity.id }) {
            it[column] = value
        }
    }
}

abstract class ImmutableCachedEntityClass<ID:Any, out T: Entity<ID>>(table: IdTable<ID>, entityType: Class<T>? = null) : ImmutableEntityClass<ID, T>(table, entityType) {

    private val cacheLoadingState = Key<Any>()
    private var _cachedValues: MutableMap<Any, Entity<Any>>? = null

    override fun invalidateEntityInCache(o: Entity<ID>) {
        warmCache()
    }

    final override fun warmCache(): EntityCache {
        val tr = TransactionManager.current()
        val transactionCache = super.warmCache()
        if (_cachedValues == null) synchronized(this) {
            when {
                _cachedValues != null -> {} // already loaded in another transaction
                tr.getUserData(cacheLoadingState) != null -> {
                    return transactionCache // prevent recursive call to warmCache() in .all()
                }
                else -> {
                    tr.putUserData(cacheLoadingState, this)
                    super.all().toList()  /* force iteration to initialize lazy collection */
                    _cachedValues = transactionCache.data[table] ?: mutableMapOf()
                    tr.removeUserData(cacheLoadingState)
                }
            }
        }
        transactionCache.data[table] = _cachedValues!!
        return transactionCache
    }

    override fun all(): SizedIterable<T> = SizedCollection(warmCache().findAll(this))

    @Synchronized fun expireCache() {
        _cachedValues = null
    }

    override fun <T> forceUpdateEntity(entity: Entity<ID>, column: Column<T>, value: T) {
        super.forceUpdateEntity(entity, column, value)
        entity._readValues?.set(column, value)
        expireCache()
    }
}
