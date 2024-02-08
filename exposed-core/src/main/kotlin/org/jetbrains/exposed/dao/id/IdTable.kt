package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.*
import java.util.*
import kotlin.collections.HashSet

/** Base class representing a producer of [EntityID] instances.  */
interface EntityIDFactory {
    /** Returns a new [EntityID] that holds a [value] of type [T], for the specified [table]. */
    fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T>
}

/** Class responsible for locating and providing the appropriate functions to produce [EntityID] instances. */
object EntityIDFunctionProvider {
    private val factory: EntityIDFactory
    init {
        factory = ServiceLoader.load(EntityIDFactory::class.java, EntityIDFactory::class.java.classLoader).firstOrNull()
            ?: object : EntityIDFactory {
                override fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
                    return EntityID(value, table)
                }
            }
    }

    /** Returns a new [EntityID] that holds a [value] of type [T], for the specified [table]. */
    fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>) = factory.createEntityID(value, table)
}

/**
 * Base class for an identity table, which could be referenced from other tables.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 */
abstract class IdTable<T : Comparable<T>>(name: String = "") : Table(name) {
    /** The identity column of this [IdTable], for storing values of type [T] wrapped as [EntityID] instances. */
    abstract val id: Column<EntityID<T>>
}

abstract class CompositeIdTable(name: String = "") : IdTable<CompositeID>(name) {
    val idColumns = HashSet<Column<out Comparable<*>>>()

    fun compositeEntityId(
        firstColumn: Column<out Comparable<*>>,
        vararg columns: Column<out Comparable<*>>
    ): Column<EntityID<CompositeID>> {
        idColumns.addAll(arrayOf(firstColumn) + columns)
        return Column<EntityID<CompositeID>>(this, "id", EntityIDColumnType(firstColumn as Column<out Comparable<Any>>)).also {
            val compositeID = CompositeID().apply {
                idColumns.forEach { column -> put(column, column.defaultValueFun?.let { it() }) }
            }
            it.defaultValueFun = { EntityIDFunctionProvider.createEntityID(compositeID, this) }
        }
    }
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `Int` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name) {
    /** The identity column of this [IntIdTable], for storing 4-byte integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `UInt` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class UIntIdTable(name: String = "", columnName: String = "id") : IdTable<UInt>(name) {
    /** The identity column of this [IntIdTable], for storing 4-byte unsigned integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<UInt>> = uinteger(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `Long` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name) {
    /** The identity column of this [LongIdTable], for storing 8-byte integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-incrementing `ULong` value.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class ULongIdTable(name: String = "", columnName: String = "id") : IdTable<ULong>(name) {
    /** The identity column of this [ULongIdTable], for storing 8-byte unsigned integers wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<ULong>> = ulong(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with a primary key consisting of an auto-generating [UUID] value.
 *
 * **Note** The specific UUID column type used depends on the database.
 * The stored identity value will be auto-generated on the client side just before insertion of a new row.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    /** The identity column of this [UUIDTable], for storing UUIDs wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<UUID>> = uuid(columnName).autoGenerate().entityId()
    final override val primaryKey = PrimaryKey(id)
}
