package kotlin.dao

import kotlin.sql.*
import java.util.HashMap
import java.util.LinkedHashMap
import kotlin.properties.Delegates

/**
 * @author max
 */
class EntityID(val id: Int, val factory: EntityClass<*>) {
    fun table() = factory.table
}

private fun <T:Int?>checkReference(reference: Column<T>, factory: EntityClass<*>) {
    val refColumn = reference.referee
    if (refColumn == null) throw RuntimeException("Column $reference is not a reference")
    val targetTable = refColumn.table
    if (factory.table != targetTable) {
        throw RuntimeException("Column and factory point to different tables")
    }
}

class Reference<Target : Entity> (val reference: Column<Int>, val factory: EntityClass<Target>) {
    {
        checkReference(reference, factory)
    }
}

class OptionalReference<Target: Entity> (val reference: Column<Int?>, val factory: EntityClass<Target>) {
    {
        checkReference(reference, factory)
    }
}

class Referrers<Source:Entity>(val reference: Column<Int>, val factory: EntityClass<Source>) {
    {
        val refColumn = reference.referee
        if (refColumn == null) throw RuntimeException("Column $reference is not a reference")

        if (factory.table != reference.table) {
            throw RuntimeException("Column and factory point to different tables")
        }
    }

    fun get(o: Entity, desc: jet.PropertyMetadata): Iterable<Source> {
        return factory.find(reference.equals(o.id.id))
    }
}

// TODO: add caching
class View<Target: Entity> (val op : Op, val factory: EntityClass<Target>) {
    fun get(o: Any, desc: jet.PropertyMetadata): Iterable<Target> = factory.find(op)
}

open public class Entity(val id: EntityID) {
    val writeValues = LinkedHashMap<Column<*>, Any?>()
    val readValues: ResultRow by Delegates.lazy {
        with(Session.get()) {
            val table = id.table()
            table.select(table.id.equals(id.id))
        }.first()
    }

    fun <T: Entity> Reference<T>.get(o: Entity, desc: jet.PropertyMetadata): T {
        return factory.findById(reference.get(o, desc))!!
    }

    fun <T: Entity> Reference<T>.set(o: Entity, desc: jet.PropertyMetadata, value: T) {
        reference.set(o, desc, value.id.id)
    }

    fun <T: Entity> OptionalReference<T>.get(o: Entity, desc: jet.PropertyMetadata): T? {
        return reference.get(o, desc)?.let{factory.findById(it)}
    }

    fun <T: Entity> OptionalReference<T>.set(o: Entity, desc: jet.PropertyMetadata, value: T?) {
        reference.set(o, desc, value?.id?.id)
    }

    fun <T> Column<T>.get(o: Entity, desc: jet.PropertyMetadata): T {
        if (id.id == -1) {
            throw RuntimeException("Prototypes are write only")
        }
        else {
            if (writeValues.containsKey(this)) {
                return writeValues[this] as T
            }
            return readValues[this]
        }
    }

    fun <T> Column<T>.set(o: Entity, desc: jet.PropertyMetadata, value: T) {
        writeValues[this] = value
    }

    fun flush() {
        if (!writeValues.isEmpty()) {
            with(Session.get()) {
                val table = id.table()
                table.update(table.id.equals(id.id)) {
                    for ((c, v) in writeValues) {
                        it[c as Column<Any?>] = v
                    }
                }
            }
        }
    }
}

class EntityCache {
    val data = HashMap<EntityClass<*>, MutableMap<Int, *>>()

    private fun <T: Entity> getMap(f: EntityClass<T>) : MutableMap<Int, T> {
        return data.get(f) as MutableMap<Int, T>? ?: run {
            val new = HashMap<Int, T>()
            data[f] = new
            new
        }
    }

    fun <T: Entity> find(f: EntityClass<T>, id: Int): T? {
        return getMap(f)[id]
    }

    fun <T: Entity> store(f: EntityClass<T>, o: T) {
        getMap(f).put(o.id.id, o)
    }

    fun flush() {
        for ((f, map) in data) {
            for ((i, p) in map) {
                (p as Entity).flush()
            }
        }
    }

    class object {
        val key = Key<EntityCache>()
        val newCache = { EntityCache()}

        fun getOrCreate(s: Session): EntityCache {
            return s.getOrCreate(key, newCache)
        }
    }
}

abstract public class EntityClass<out T: Entity>() {
    abstract val table: IdTable

    private val klass = javaClass.getEnclosingClass()!!
    private val cons = klass.getConstructors()[0]

    public fun get(id: Int): T {
        return findById(id)!!
    }

    public fun findById(id: Int): T? {
        return find(table.id.equals(id)).firstOrNull()
    }

    public fun find(op: Op): Iterable<T> {
        return with (Session.get()) {
            table.select(op) mapLazy {wrap(it[table.id], this)}
        }
    }

    protected open fun createInstance(entityId: EntityID) : T = cons.newInstance(entityId) as T

    public fun wrap(id: Int, s: Session): T {
        val cache = EntityCache.getOrCreate(s)
        return cache.find(this, id) ?: run {
            val new = createInstance(EntityID(id, this))
            cache.store(this, new)
            new
        }
    }

    public fun new(init: T.() -> Unit) : T {
        val prototype = createInstance(EntityID(-1, this))
        prototype.init()

        val row = InsertQuery(table)
        for ((c, v) in prototype.writeValues) {
            row.set(c as Column<Any?>, v)
        }

        val session = Session.get()
        row.execute(session)
        return wrap(row get table.id, session)
    }

    public fun view (op: Op) : View<T>  = View(op, this)

    public fun referencedOn(column: Column<Int>): Reference<T> {
        return Reference(column, this)
    }

    public fun optionalReferencedOn(column: Column<Int?>): OptionalReference<T> {
        return OptionalReference(column, this)
    }

    public fun referrersOn(column: Column<Int>): Referrers<T> {
        return Referrers(column, this)
    }
}
