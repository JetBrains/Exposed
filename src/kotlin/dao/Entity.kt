package kotlin.dao

import java.util.*
import kotlin.properties.Delegates
import kotlin.reflect.KProperty
import kotlin.sql.*

/**
 * @author max
 */
public class EntityID(id: Int, val table: IdTable) {
    var _value = id
    val value: Int get() {
        if (_value == -1) {
            EntityCache.getOrCreate(Session.get()).flushInserts(table)
            assert(_value > 0) { "Entity must be inserted" }
        }

        return _value
    }

    override fun toString() = value.toString()

    override fun hashCode() = value

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID) return false

        return other._value == _value && other.table == table
    }
}

private fun <T:EntityID?>checkReference(reference: Column<T>, factory: EntityClass<*>) {
    val refColumn = reference.referee
    if (refColumn == null) error("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factory.table != targetTable) {
        error("Column and factory point to different tables")
    }
}

class Reference<out Target : Entity> (val reference: Column<EntityID>, val factory: EntityClass<Target>) {
    init {
        checkReference(reference, factory)
    }
}

class OptionalReference<out Target: Entity> (val reference: Column<EntityID?>, val factory: EntityClass<Target>) {
    init {
        checkReference(reference, factory)
    }
}

class OptionalReferenceSureNotNull<out Target: Entity> (val reference: Column<EntityID?>, val factory: EntityClass<Target>) {
    init {
        checkReference(reference, factory)
    }
}

class Referrers<out Source:Entity>(val reference: Column<EntityID>, val factory: EntityClass<Source>, val cache: Boolean) {
    init {
        val refColumn = reference.referee
        if (refColumn == null) error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    operator fun getValue(o: Entity, desc: KProperty<*>): SizedIterable<Source> {
        val query = {factory.find{reference eq o.id}}
        return if (cache) EntityCache.getOrCreate(Session.get()).getOrPutReferrers(o, reference, query)  else query()
    }
}

class OptionalReferrers<out Source:Entity>(val reference: Column<EntityID?>, val factory: EntityClass<Source>, val cache: Boolean) {
    init {
        val refColumn = reference.referee ?: error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    operator fun getValue(o: Entity, desc: KProperty<*>): SizedIterable<Source> {
        val query = {factory.find{reference eq o.id}}
        return if (cache) EntityCache.getOrCreate(Session.get()).getOrPutReferrers(o, reference, query)  else query()
    }
}

open class ColumnWithTransform<TColumn, TReal>(val column: Column<TColumn>, val toColumn: (TReal) -> TColumn, val toReal: (TColumn) -> TReal) {
}

public class View<out Target: Entity> (val op : Op<Boolean>, val factory: EntityClass<Target>) : SizedIterable<Target> {
    override fun limit(n: Int): SizedIterable<Target> = factory.find(op).limit(n)
    override fun count(): Int = factory.find(op).count()
    override fun empty(): Boolean = factory.find(op).empty()
    override fun forUpdate(): SizedIterable<Target> = factory.find(op).forUpdate()
    override fun notForUpdate(): SizedIterable<Target> = factory.find(op).notForUpdate()

    operator public override fun iterator(): Iterator<Target> = factory.find(op).iterator()
    operator fun getValue(o: Any?, desc: KProperty<*>): SizedIterable<Target> = factory.find(op)
}

@Suppress("UNCHECKED_CAST")
class InnerTableLink<Target: Entity>(val table: Table,
                                     val target: EntityClass<Target>) {
    private fun getSourceRefColumn(o: Entity): Column<EntityID> {
        val sourceRefColumn = table.columns.firstOrNull { it.referee == o.klass.table.id } as? Column<EntityID> ?: error("Table does not reference source")
        return sourceRefColumn
    }

    private fun getTargetRefColumn(): Column<EntityID> {
        val sourceRefColumn = table.columns.firstOrNull { it.referee == target.table.id } as? Column<EntityID> ?: error("Table does not reference source")
        return sourceRefColumn
    }

    operator fun getValue(o: Entity, desc: KProperty<*>): SizedIterable<Target> {
        fun alreadyInJoin() = (target.dependsOnTables as? Join)?.joinParts?.any { it.joinType == JoinType.INNER && it.table == table} ?: false
        val sourceRefColumn = getSourceRefColumn(o)
        val entityTables: ColumnSet = when {
            target.dependsOnTables is Table -> (target.dependsOnTables as Table).innerJoin(table)
            alreadyInJoin() -> target.dependsOnTables
            else -> (target.dependsOnTables as Join).innerJoin(table)
        }
        val columns = (target.dependsOnColumns + (if (!alreadyInJoin()) table.columns else emptyList())
            - sourceRefColumn).distinct() + sourceRefColumn

        val query = {target.wrapRows(entityTables.slice(columns).select{sourceRefColumn eq o.id})}
        return EntityCache.getOrCreate(Session.get()).getOrPutReferrers(o, sourceRefColumn, query)
    }

    operator fun setValue(o: Entity, desc: KProperty<*>, value: SizedIterable<Target>) {
        val sourceRefColumn = getSourceRefColumn(o)
        val targeRefColumn = getTargetRefColumn()

        with(Session.get()) {
            val entityCache = EntityCache.getOrCreate(Session.get())
            entityCache.flush()
            val existingIds = getValue(o, desc).map { it.id }.toSet()
            entityCache.clearReferrersCache()

            val targetIds = value.map { it.id }.toList()
            table.deleteWhere { (sourceRefColumn eq o.id) and (targeRefColumn notInList targetIds) }
            table.batchInsert(targetIds.filter { !existingIds.contains(it) }) { targetId ->
                this[sourceRefColumn] = o.id
                this[targeRefColumn] = targetId
            }
        }
    }
}

open public class Entity(val id: EntityID) {
    var klass: EntityClass<*> by Delegates.notNull()

    val writeValues = LinkedHashMap<Column<Any?>, Any?>()
    var _readValues: ResultRow? = null
    val readValues: ResultRow
    get() {
        return _readValues ?: run {
            val table = klass.table
            _readValues = klass.searchQuery( Op.build {table.id eq id }).firstOrNull() ?: table.select { table.id eq id }.first()
            _readValues!!
        }
    }

    /*private val cachedData = LinkedHashMap<String, Any>()
    public fun<T> getOrCreate(key: String, evaluate: ()->T) : T {
        return cachedData.getOrPut(key, evaluate) as T
    }*/

    operator fun <T: Entity> Reference<T>.getValue(o: Entity, desc: KProperty<*>): T {
        val id = reference.getValue(o, desc)
        return factory.findById(id) ?: error("Cannot find ${factory.table.tableName} WHERE id=$id")
    }

    operator fun <T: Entity> Reference<T>.setValue(o: Entity, desc: KProperty<*>, value: T) {
        reference.setValue(o, desc, value.id)
    }

    operator fun <T: Entity> OptionalReference<T>.getValue(o: Entity, desc: KProperty<*>): T? {
        return reference.getValue(o, desc)?.let{factory.findById(it)}
    }

    operator fun <T: Entity> OptionalReference<T>.setValue(o: Entity, desc: KProperty<*>, value: T?) {
        reference.setValue(o, desc, value?.id)
    }

    operator fun <T: Entity> OptionalReferenceSureNotNull<T>.getValue(o: Entity, desc: KProperty<*>): T {
        val id = reference.getValue(o, desc) ?: error("${o.id}.$desc is null")
        return factory.findById(id) ?: error("Cannot find ${factory.table.tableName} WHERE id=$id")
    }

    operator fun <T: Entity> OptionalReferenceSureNotNull<T>.setValue(o: Entity, desc: KProperty<*>, value: T) {
        reference.setValue(o, desc, value.id)
    }

    operator fun <T> Column<T>.getValue(o: Entity, desc: KProperty<*>): T {
        return lookup()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T, R:Any> Column<T>.lookupInReadValues(found: (T?) -> R?, notFound: () -> R?): R? {
        if (_readValues?.hasValue(this) ?: false)
            return found(readValues[this])
        else
            return notFound()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Any?> Column<T>.lookup(): T = when {
        writeValues.containsKey(this) -> writeValues.get(this) as T
        id._value == -1 && _readValues?.hasValue(this)?.not() ?: true -> defaultValue as T
        else -> readValues[this]
    }

    operator fun <T> Column<T>.setValue(o: Entity, desc: KProperty<*>, value: T) {
        if (writeValues.containsKey(this) || _readValues?.tryGet(this) != value) {
            if (referee != null) {
                EntityCache.getOrCreate(Session.get()).referrers.run {
                    filterKeys { it.id == value }.forEach {
                        if (it.value.keys.any { it == this@setValue } ) {
                            this.remove(it.key)
                        }
                    }
                }
                EntityCache.getOrCreate(Session.get()).removeTablesReferrers(listOf(referee!!.table))
            }
            writeValues.set(this as Column<Any?>, value)
        }
    }

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.getValue(o: Entity, desc: KProperty<*>): TReal {
        return toReal(column.getValue(o, desc))
    }

    operator fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.setValue(o: Entity, desc: KProperty<*>, value: TReal) {
        column.setValue(o, desc, toColumn(value))
    }

    infix public fun <Target:Entity> EntityClass<Target>.via(table: Table): InnerTableLink<Target> {
        return InnerTableLink(table, this@via)
    }

    public fun <T: Entity> s(c: EntityClass<T>): EntityClass<T> = c

    public open fun delete(){
        klass.removeFromCache(this)
        val table = klass.table
        table.deleteWhere{table.id eq id}
    }

    open fun flush(batch: BatchUpdateQuery? = null): Boolean {
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

    public  fun storeWrittenValues() {
        // move write values to read values
        if (_readValues != null) {
            for ((c, v) in writeValues) {
                _readValues!!.set(c, v?.let { c.columnType.valueFromDB(c.columnType.valueToDB(it)!!)})
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
    val data = HashMap<IdTable, MutableMap<Int, Entity>>()
    val inserts = HashMap<IdTable, MutableList<Entity>>()
    val referrers = HashMap<Entity, MutableMap<Column<*>, SizedIterable<*>>>()

    private fun <T: Entity> getMap(f: EntityClass<T>) : MutableMap<Int, T> {
        return getMap(f.table)
    }

    private fun <T: Entity> getMap(table: IdTable) : MutableMap<Int, T> {
        val answer = data.getOrPut(table, {
            HashMap()
        }) as MutableMap<Int, T>

        return answer
    }

    fun <T: Entity, R: Entity> getOrPutReferrers(source: T, key: Column<*>, refs: ()-> SizedIterable<R>): SizedIterable<R> {
        return referrers.getOrPut(source, {HashMap()}).getOrPut(key, {LazySizedCollection(refs())}) as SizedIterable<R>
    }

    fun <T: Entity> find(f: EntityClass<T>, id: EntityID): T? {
        return getMap(f)[id.value]
    }

    fun <T: Entity> findAll(f: EntityClass<T>): SizedIterable<T> {
        return SizedCollection(getMap(f).values)
    }

    fun <T: Entity> store(f: EntityClass<T>, o: T) {
        getMap(f).put(o.id.value, o)
    }

    fun <T: Entity> store(table: IdTable, o: T) {
        getMap<T>(table).put(o.id.value, o)
    }

    fun <T: Entity> remove(table: IdTable, o: T) {
        getMap<T>(table).remove(o.id.value)
    }

    fun <T: Entity> scheduleInsert(f: EntityClass<T>, o: T) {
        val list = inserts.getOrPut(f.table) {
            ArrayList<Entity>()
        }

        list.add(o)
    }

    infix private fun Table.references(another: Table): Boolean {
        return columns.any { it.referee?.table == another }
    }

    private fun<T> swap (list: ArrayList<T>, i : Int, j: Int) {
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }

    fun<T> ArrayList<T>.topoSort (comparer: (T,T) -> Int) {
        for (i in 0..this.size -2) {
            var minIndex = i
            for (j in (i+1)..this.size -1) {
                if (comparer(this[minIndex], this[j]) > 0) {
                    minIndex = j
                }
            }
            if (minIndex != i)
                swap(this, i, minIndex)
        }
    }

    fun flush() {
        flush((inserts.keys + data.keys).toSet())
    }

    fun addDependencies(tables: Iterable<IdTable>): Iterable<IdTable> {
        val workset = HashSet<IdTable>()

        fun checkTable(table: IdTable) {
            if (workset.add(table)) {
                for (c in table.columns) {
                    val referee = c.referee
                    if (referee != null) {
                        if (referee.table is IdTable) checkTable(referee.table)
                    }
                }
            }
        }

        for (t in tables) checkTable(t)

        return workset
    }

    fun flush(tables: Iterable<IdTable>) {
        val sorted = addDependencies(tables).toArrayList()
        sorted.topoSort { a, b ->
            when {
                a == b -> 0
                a references b && b references a -> 0
                a references b -> 1
                b references a -> -1
                else -> 0
            }
        }

        val insertedTables = inserts.map { it.key }

        for (t in sorted) {
            flushInserts(t)
        }

        for (t in tables) {
            val map = data[t]
            if (map != null) {
                val updatedEntities = HashSet<Entity>()
                val batch = BatchUpdateQuery(t)
                for ((i, entity) in map) {
                    if (entity.flush(batch)) {
                        if (entity.klass is ImmutableEntityClass<*>) {
                            throw IllegalStateException("Update on immutable entity ${entity.javaClass.simpleName} ${entity.id}")
                        }
                        updatedEntities.add(entity)
                    }
                }
                batch.execute(Session.get())
                updatedEntities.forEach {
                    EntityHook.alertSubscribers(it, false)
                }
            }
        }

        if (insertedTables.isNotEmpty()) {
            removeTablesReferrers(insertedTables)
        }
    }

    internal fun removeTablesReferrers(insertedTables: List<Table>) {
        referrers.filterValues { it.any { it.key.table in insertedTables } }.map { it.key }.forEach {
            referrers.remove(it)
        }
    }

    fun flushInserts(table: IdTable) {
        inserts.remove(table)?.let {
            val ids = table.batchInsert(it) { entry ->
                for ((c, v) in entry.writeValues) {
                    this[c] = v
                }
            }

            for ((entry, id) in it.zip(ids)) {
                entry.id._value = id
                entry.writeValues.set(entry.klass.table.id as Column<Any?>, id)
                entry.storeWrittenValues()
                EntityCache.getOrCreate(Session.get()).store(table, entry)
                EntityHook.alertSubscribers(entry, true)
            }
        }
    }

    fun clearReferrersCache() {
        referrers.clear()
    }

    companion object {
        val key = Key<EntityCache>()
        val newCache = { EntityCache()}

        fun invalidateGlobalCaches(created: List<Entity>) {
            created.map { it.klass }.filterNotNull().filterIsInstance<ImmutableCachedEntityClass<*>>().toSet().forEach {
                it.expireCache()
            }
        }

        fun getOrCreate(s: Session): EntityCache {
            return s.getOrCreate(key, newCache)
        }
    }
}

@Suppress("UNCHECKED_CAST")
abstract public class EntityClass<out T: Entity>(val table: IdTable) {
    private val klass = javaClass.enclosingClass!!
    private val ctor = klass.constructors[0]

    public operator fun get(id: EntityID): T {
        return findById(id) ?: error("Entity not found in database")
    }

    public operator fun get(id: Int): T {
        return findById(id) ?: error("Entity not found in database")
    }

    open protected fun warmCache(): EntityCache = EntityCache.getOrCreate(Session.get())

    public fun findById(id: Int): T? {
        return findById(EntityID(id, table))
    }

    public fun findById(id: EntityID): T? {
        return testCache(id) ?: find{table.id eq id}.firstOrNull()
    }

    public fun reload(entity: Entity): T? {
        removeFromCache(entity)
        return find { table.id eq entity.id }.firstOrNull()
    }

    public fun testCache(id: EntityID): T? {
        return warmCache().find(this, id)
    }

    fun testCache(cacheCheckCondition: T.()->Boolean): List<T> = warmCache().findAll(this).filter { it.cacheCheckCondition() }

    fun removeFromCache(entity: Entity) {
        val cache = warmCache()
        cache.remove(table, entity)
        cache.referrers.remove(entity)
        cache.removeTablesReferrers(listOf(table))
    }

    public fun forEntityIds(ids: List<EntityID>) : SizedIterable<T> {
        val cached = ids.map { testCache(it) }.filterNotNull()
        if (cached.size == ids.size) {
            return SizedCollection(cached)
        }
        return wrapRows(searchQuery(Op.build {table.id inList ids}))
    }

    public fun forIds(ids: List<Int>) : SizedIterable<T> {
        val cached = ids.map { testCache(EntityID(it, table)) }.filterNotNull()
        if (cached.size == ids.size) {
            return SizedCollection(cached)
        }

        return wrapRows(searchQuery(Op.build {table.id inList ids.map {EntityID(it, table)}}))
    }

    public fun wrapRows(rows: SizedIterable<ResultRow>): SizedIterable<T> {
        val session = Session.get()
        return rows mapLazy {
            wrapRow(it, session)
        }
    }

    public fun wrapRow (row: ResultRow, session: Session) : T {
        val entity = wrap(row[table.id], row, session)
        if (entity._readValues == null)
            entity._readValues = row

        return entity
    }

    open public fun all(): SizedIterable<T> = wrapRows(table.selectAll().notForUpdate())

    public fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    public fun find(op: SqlExpressionBuilder.()->Op<Boolean>): SizedIterable<T> {
        warmCache()
        return find(SqlExpressionBuilder.op())
    }

    fun findWithCacheCondition(cacheCheckCondition: T.()->Boolean, op: SqlExpressionBuilder.()->Op<Boolean>): SizedIterable<T> {
        val cached = testCache(cacheCheckCondition)
        return if (cached.isNotEmpty()) SizedCollection(cached) else find(op)
    }

    open val dependsOnTables: ColumnSet get() = table
    open val dependsOnColumns: List<Column<out Any?>> get() = dependsOnTables.columns

    open fun searchQuery(op: Op<Boolean>): Query {
        return dependsOnTables.slice(dependsOnColumns).select { op }.setForUpdateStatus()
    }

    public fun count(op: Op<Boolean>? = null): Int {
        return with (Session.get()) {
            val query = table.slice(table.id.count())
            (if (op == null) query.selectAll() else query.select{op}).notForUpdate().first()[
                table.id.count()
            ]
        }
    }

    protected open fun createInstance(entityId: EntityID, row: ResultRow?) : T = ctor.newInstance(entityId) as T

    public fun wrap(id: EntityID, row: ResultRow?, s: Session): T {
        val cache = EntityCache.getOrCreate(s)
        return cache.find(this, id) ?: run {
            val new = createInstance(id, row)
            new.klass = this
            cache.store(this, new)
            new
        }
    }

    public fun new(init: T.() -> Unit): T {
        val prototype: T = createInstance(EntityID(-1, table), null)
        prototype.klass = this
        prototype._readValues = ResultRow.create(dependsOnColumns)
        prototype.init()
        warmCache().scheduleInsert(this, prototype)
        return prototype
    }

    public inline fun view (op: SqlExpressionBuilder.() -> Op<Boolean>) : View<T>  = View(SqlExpressionBuilder.op(), this)

    infix public fun referencedOn(column: Column<EntityID>): Reference<T> {
        return Reference(column, this)
    }

    infix public fun optionalReferencedOn(column: Column<EntityID?>): OptionalReference<T> {
        return OptionalReference(column, this)
    }

    infix public fun optionalReferencedOnSureNotNull(column: Column<EntityID?>): OptionalReferenceSureNotNull<T> {
        return OptionalReferenceSureNotNull(column, this)
    }

    public fun referrersOn(column: Column<EntityID>, cache: Boolean = false): Referrers<T> {
        return Referrers(column, this, cache)
    }

    //TODO: what's the difference with referrersOn?
    public fun optionalReferrersOn(column: Column<EntityID?>, cache: Boolean = false): OptionalReferrers<T> {
        return OptionalReferrers(column, this, cache)
    }

    fun<TColumn: Any?,TReal: Any?> Column<TColumn>.transform(toColumn: (TReal) -> TColumn, toReal: (TColumn) -> TReal): ColumnWithTransform<TColumn, TReal> {
        return ColumnWithTransform(this, toColumn, toReal)
    }

    fun<TReal: Enum<TReal>> Column<String?>.byEnumNullable(clazz : Class<TReal>): ColumnWithTransform<String?, TReal?> {
        return ColumnWithTransform(this, { it?.name }, {it?.let{clazz.findValue(it)}})
    }

    fun<TReal: Enum<TReal>> Column<String>.byEnum(clazz : Class<TReal>): ColumnWithTransform<String, TReal> {
        return ColumnWithTransform(this, { it.name }, {clazz.findValue(it)})
    }

    fun <T: Enum<T>> Class<T>.findValue(name: String) = enumConstants.first { it.name == name }

    private fun Query.setForUpdateStatus(): Query = if (this@EntityClass is ImmutableEntityClass<*>) this.notForUpdate() else this
}

abstract public class ImmutableEntityClass<out T: Entity>(table: IdTable) : EntityClass<T>(table) {
    open public fun <T> forceUpdateEntity(entity: Entity, column: Column<T>, value: T?) {
        table.update({ table.id eq entity.id }) {
            it[column] = value
        }
    }
}

abstract public class ImmutableCachedEntityClass<T: Entity>(table: IdTable) : ImmutableEntityClass<T>(table) {

    private var _cachedValues: MutableMap<Int, Entity>? = null

    final override fun warmCache(): EntityCache {
        val sessionCache = super.warmCache()
        if (_cachedValues == null) synchronized(this) {
            for(r in super.all()) {  /* force iteration to initialize lazy collection */ }
            _cachedValues = sessionCache.data[table]
        } else {
            sessionCache.data.getOrPut(table) { _cachedValues!! }
        }

        return sessionCache
    }

    override fun all(): SizedIterable<T> = warmCache().findAll(this)

    public @Synchronized fun expireCache() {
        _cachedValues = null
    }

    override public fun <T> forceUpdateEntity(entity: Entity, column: Column<T>, value: T?) {
        super.forceUpdateEntity(entity, column, value)
        entity._readValues?.set(column, value)
        expireCache()
    }
}
