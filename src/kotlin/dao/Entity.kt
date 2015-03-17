package kotlin.dao

import kotlin.sql.*
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.ArrayList
import java.util.HashSet

/**
 * @author max
 */
public class EntityID(id: Int, val table: IdTable) {
    var _value = id
    val value: Int get() {
        if (_value == -1) {
            EntityCache.getOrCreate(Session.get()).flushInserts(table)
            assert(_value > 0, "Entity must be inserted")
        }

        return _value
    }

    override fun toString() = value.toString()

    override fun hashCode(): Int {
        if (_value == -1) error("The value is going to be changed, thus hashCode too!")
        return _value
    }

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
    {
        checkReference(reference, factory)
    }
}

class OptionalReference<out Target: Entity> (val reference: Column<EntityID?>, val factory: EntityClass<Target>) {
    {
        checkReference(reference, factory)
    }
}

class OptionalReferenceSureNotNull<out Target: Entity> (val reference: Column<EntityID?>, val factory: EntityClass<Target>) {
    {
        checkReference(reference, factory)
    }
}

class Referrers<out Source:Entity>(val reference: Column<EntityID>, val factory: EntityClass<Source>, val cache: Boolean) {
    {
        val refColumn = reference.referee
        if (refColumn == null) error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    fun get(o: Entity, desc: kotlin.PropertyMetadata): SizedIterable<Source> {
        val query = {factory.find{reference eq o.id}}
        return if (cache) EntityCache.getOrCreate(Session.get()).getOrPutReferrers(o, reference, query)  else query()
    }
}

class OptionalReferrers<out Source:Entity>(val reference: Column<EntityID?>, val factory: EntityClass<Source>, val cache: Boolean) {
    {
        val refColumn = reference.referee
        if (refColumn == null) error("Column $reference is not a reference")

        if (factory.table != reference.table) {
            error("Column and factory point to different tables")
        }
    }

    fun get(o: Entity, desc: kotlin.PropertyMetadata): SizedIterable<Source> {
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
    public override fun iterator(): Iterator<Target> = factory.find(op).iterator()
    fun get(o: Any?, desc: kotlin.PropertyMetadata): SizedIterable<Target> = factory.find(op)
}

class InnerTableLink<Target: Entity>(val table: Table,
                                     val target: EntityClass<Target>) {
    fun get(o: Entity, desc: kotlin.PropertyMetadata): SizedIterable<Target> {
        val sourceRefColumn = table.columns.firstOrNull { it.referee == o.factory().table.id } as? Column<EntityID> ?: error("Table does not reference source")

        val query = {target.wrapRows(target.table.innerJoin(table).select{sourceRefColumn eq o.id})}
        return EntityCache.getOrCreate(Session.get()).getOrPutReferrers(o, sourceRefColumn, query)
    }
}

open public class Entity(val id: EntityID) {
    var klass: EntityClass<*>? = null

    val writeValues = LinkedHashMap<Column<Any?>, Any?>()
    var _readValues: ResultRow? = null
    val readValues: ResultRow
    get() {
        return _readValues ?: run {
            val table = factory().table
            _readValues = table.select{table.id eq id}.first()
            _readValues!!
        }
    }

    /*private val cachedData = LinkedHashMap<String, Any>()
    public fun<T> getOrCreate(key: String, evaluate: ()->T) : T {
        return cachedData.getOrPut(key, evaluate) as T
    }*/

    public fun factory(): EntityClass<*> = klass!!

    fun <T: Entity> Reference<T>.get(o: Entity, desc: kotlin.PropertyMetadata): T {
        val id = reference.get(o, desc)
        return factory.findById(id) ?: error("Cannot find ${factory.table.tableName} WHERE id=$id")
    }

    fun <T: Entity> Reference<T>.set(o: Entity, desc: kotlin.PropertyMetadata, value: T) {
        reference.set(o, desc, value.id)
    }

    fun <T: Entity> OptionalReference<T>.get(o: Entity, desc: kotlin.PropertyMetadata): T? {
        return reference.get(o, desc)?.let{factory.findById(it)}
    }

    fun <T: Entity> OptionalReference<T>.set(o: Entity, desc: kotlin.PropertyMetadata, value: T?) {
        reference.set(o, desc, value?.id)
    }

    fun <T: Entity> OptionalReferenceSureNotNull<T>.get(o: Entity, desc: kotlin.PropertyMetadata): T {
        val id = reference.get(o, desc) ?: error("${o.id}.$desc is null")
        return factory.findById(id) ?: error("Cannot find ${factory.table.tableName} WHERE id=$id")
    }

    fun <T: Entity> OptionalReferenceSureNotNull<T>.set(o: Entity, desc: kotlin.PropertyMetadata, value: T) {
        reference.set(o, desc, value.id)
    }

    fun <T> Column<T>.get(o: Entity, desc: kotlin.PropertyMetadata): T {
        return lookup()
    }

    fun <T> Column<T>.lookup(): T {
        if (writeValues.containsKey(this)) {
            return writeValues[this] as T
        }
        return readValues[this]
    }

    fun <T> Column<T>.set(o: Entity, desc: kotlin.PropertyMetadata, value: T) {
        if (writeValues.containsKey(this) || _readValues == null || _readValues!![this] != value) {
            if (referee != null) {
                EntityCache.getOrCreate(Session.get()).clearReferrersCache()
            }
            writeValues.set(this as Column<Any?>, value)
        }
    }

    fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.get(o: Entity, desc: kotlin.PropertyMetadata): TReal {
        return toReal(column.get(o, desc))
    }

    fun <TColumn, TReal> ColumnWithTransform<TColumn, TReal>.set(o: Entity, desc: kotlin.PropertyMetadata, value: TReal) {
        column.set(o, desc, toColumn(value))
    }

    public fun <Target:Entity> EntityClass<Target>.via(table: Table): InnerTableLink<Target> {
        return InnerTableLink(table, this@via)
    }

    public fun <T: Entity> s(c: EntityClass<T>): EntityClass<T> = c

    public fun delete(){
        factory().removeFromCache(this)
        val table = factory().table
        table.deleteWhere{table.id eq id}
    }

    open fun flush(batch: BatchUpdateQuery? = null) {
        if (!writeValues.isEmpty()) {
            if (batch == null) {
                val table = factory().table
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

            // move write values to read values
            if (_readValues != null) {
                for ((c, v) in writeValues) {
                    _readValues!!.data.remove(c)
                    _readValues!!.data.put(c, v)
                }
            }

            // clear write values
            writeValues.clear()
        }
    }
}

class EntityCache {
    val data = HashMap<IdTable, MutableMap<Int, Entity>>()
    val inserts = HashMap<IdTable, MutableList<Entity>>()
    val referrers = HashMap<Entity, MutableMap<Column<*>, SizedIterable<*>>>()

    val eagerSelected = HashSet<IdTable>()

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
        return SizedCollection(getMap(f).values())
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

    private fun Table.references(another: Table): Boolean {
        return columns.any { it.referee?.table == another }
    }

    private fun<T> swap (list: ArrayList<T>, i : Int, j: Int) {
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }

    fun<T> ArrayList<T>.topoSort (comparer: (T,T) -> Int) {
        for (i in 0..this.size-2) {
            var minIndex = i
            for (j in (i+1)..this.size-1) {
                if (comparer(this[minIndex], this[j]) > 0) {
                    minIndex = j
                }
            }
            if (minIndex != i)
                swap(this, i, minIndex)
        }
    }

    fun flush() {
        flush((inserts.keySet() + data.keySet()).toSet())
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

        for (t in sorted) {
            flushInserts(t)
        }

        for (t in tables) {
            val map = data[t]
            if (map != null) {
                val batch = BatchUpdateQuery(t)
                for ((i, entity) in map) {
                    entity.flush(batch)
                }
                batch.execute(Session.get())
            }
        }
    }

    fun flushInserts(table: IdTable) {
        inserts.remove(table)?.let {
            val query = BatchInsertQuery(table)
            for (entry in it) {
                query.addBatch()

                for ((c, v) in entry.writeValues) {
                    query.set(c, v)
                }
            }

            val session = Session.get()

            val ids = query.execute(session)
            for ((entry, id) in it.zip(ids)) {
                entry.id._value = id
                EntityCache.getOrCreate(session).store(table, entry)
            }
        }

    }

    fun clearReferrersCache() {
        referrers.clear()
    }

    companion object {
        val key = Key<EntityCache>()
        val newCache = { EntityCache()}

        fun getOrCreate(s: Session): EntityCache {
            return s.getOrCreate(key, newCache)
        }
    }
}

abstract public class EntityClass<out T: Entity>(val table: IdTable, val eagerSelect: Boolean = false) {
    private val klass = javaClass.getEnclosingClass()!!
    private val ctor = klass.getConstructors()[0]

    public fun get(id: EntityID): T {
        return findById(id) ?: error("Entity not found in database")
    }

    public fun get(id: Int): T {
        return findById(id) ?: error("Entity not found in database")
    }

    private fun warmCache(): EntityCache {
        val cache = EntityCache.getOrCreate(Session.get())
        if (eagerSelect) {
            if (!cache.eagerSelected.contains(table)) {
                for (r in retrieveAll()) {}
                cache.eagerSelected.add(table)
            }
        }

        return cache
    }

    public fun findById(id: Int): T? {
        return findById(EntityID(id, table))
    }

    public fun findById(id: EntityID): T? {
        return testCache(id) ?: find{table.id eq id}.firstOrNull()
    }

    private fun testCache(id: EntityID): T? {
        return warmCache().find(this, id)
    }

    fun removeFromCache(entity: Entity) {
        warmCache().remove(table, entity)
    }

    public fun forEntityIds(ids: List<EntityID>) : SizedIterable<T> {
        val cached = ids.map { testCache(it) }.filterNotNull()
        if (cached.size() == ids.size()) {
            return SizedCollection(cached)
        }
        return wrapRows(searchQuery(Op.build {table.id inList ids}))
    }

    public fun forIds(ids: List<Int>) : SizedIterable<T> {
        val cached = ids.map { testCache(EntityID(it, table)) }.filterNotNull()
        if (cached.size() == ids.size()) {
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
        entity._readValues = row
        return entity
    }

    public fun all(): SizedIterable<T> {
        if (eagerSelect) {
            return warmCache().findAll(this)
        }

        return retrieveAll()
    }

    private fun retrieveAll(): SizedIterable<T> {
        return wrapRows(table.selectAll())
    }

    public fun find(op: Op<Boolean>): SizedIterable<T> {
        warmCache()
        return wrapRows(searchQuery(op))
    }

    public fun find(op: SqlExpressionBuilder.()->Op<Boolean>): SizedIterable<T> {
        warmCache()
        return find(SqlExpressionBuilder.op())
    }

    fun findWithCacheCondition(cacheCheckCondition: T.()->Boolean, op: SqlExpressionBuilder.()->Op<Boolean>): Iterable<T> {
        val cached = EntityCache.getOrCreate(Session.get()).findAll(this).filter { it.cacheCheckCondition() }
        return if (cached.isNotEmpty()) cached else find(op)
    }

    protected open fun searchQuery(op: Op<Boolean>): Query {
        return table.select{op}
    }

    public fun count(op: Op<Boolean>? = null): Int {
        return with (Session.get()) {
            val query = table.slice(table.id.count())
            (if (op == null) query.selectAll() else query.select{op}).first()[
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

    public fun new(init: T.() -> Unit) : T {
        val prototype: T = createInstance(EntityID(-1, table), null)
        prototype.klass = this
        prototype.init()

        EntityCache.getOrCreate(Session.get()).scheduleInsert(this, prototype)

        return prototype
    }

    public inline fun view (op: SqlExpressionBuilder.() -> Op<Boolean>) : View<T>  = View(SqlExpressionBuilder.op(), this)

    public fun referencedOn(column: Column<EntityID>): Reference<T> {
        return Reference(column, this)
    }

    public fun optionalReferencedOn(column: Column<EntityID?>): OptionalReference<T> {
        return OptionalReference(column, this)
    }

    public fun optionalReferencedOnSureNotNull(column: Column<EntityID?>): OptionalReferenceSureNotNull<T> {
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

    fun<TReal: Enum<TReal>?> Column<String?>.byEnumNullable(clazz : Class<TReal>): ColumnWithTransform<String?, TReal?> {
        return ColumnWithTransform(this, {it?.name()}, {it?.let{clazz.findValue(it)}})
    }

    fun<TReal: Enum<TReal>> Column<String>.byEnum(clazz : Class<TReal>): ColumnWithTransform<String, TReal> {
        return ColumnWithTransform(this, { it.name() }, {clazz.findValue(it)})
    }

    fun <T: Enum<T>> Class<T>.findValue(name: String) = getEnumConstants().first {it.name() == name }
}
