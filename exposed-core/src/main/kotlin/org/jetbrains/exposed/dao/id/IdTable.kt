package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.*
import java.util.*

interface EntityIDFactory {
    fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T>
}

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

    @Suppress("UNCHECKED_CAST")
    fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>) = factory.createEntityID(value, table)
}

/**
 * Base class for an identity table which could be referenced from another tables.
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
abstract class IdTable<T : Comparable<T>>(name: String = "") : Table(name), IdAware<T> {

    /**
     * Returns a new instance of a table that will ignore the default scope when generating/running queries.
     */
    override fun stripDefaultScope() : IdTableWithDefaultScopeStriped<T> = IdTableWithDefaultScopeStriped(this)
}

/**
 * Identity table with autoincrement integer primary key
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 * @param columnName name for a primary key, "id" by default
 */
open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name) {
    final override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with autoincrement long primary key
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 * @param columnName name for a primary key, "id" by default
 */
open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name) {
    final override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/**
 * Identity table with [UUID] primary key.
 *
 * [UUID] column type depends on a database.
 *
 * Id value will be generated on a client side just before an insert of a new row.
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 * @param columnName name for a primary key, "id" by default
 */
open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    final override val id: Column<EntityID<UUID>> = uuid(columnName).autoGenerate().entityId()
    final override val primaryKey = PrimaryKey(id)
}

/** An Id table that ignores the actualTable's default scope */
open class IdTableWithDefaultScopeStriped<ID : Comparable<ID>>(actualTable: IdTable<ID>)
    : TableWithDefaultScopeStriped(actualTable), IdAware<ID> {
    override val id: Column<EntityID<ID>> = actualTable.id
}
