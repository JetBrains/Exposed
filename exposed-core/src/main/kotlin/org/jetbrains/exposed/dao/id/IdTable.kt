package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ITable
import org.jetbrains.exposed.sql.Table
import java.util.*

interface EntityIDFactory {
    fun <T:Comparable<T>> createEntityID(value: T, table: IdTableInterface<T>) : EntityID<T>
}

object EntityIDFunctionProvider {
    private val factory : EntityIDFactory
    init {
        factory = ServiceLoader.load(EntityIDFactory::class.java, EntityIDFactory::class.java.classLoader).firstOrNull()
                ?: object : EntityIDFactory {
                    override fun <T : Comparable<T>> createEntityID(value: T, table: IdTableInterface<T>): EntityID<T> {
                        return EntityID(value, table)
                    }
                }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Comparable<T>> createEntityID(value: T, table: IdTableInterface<T>) = factory.createEntityID(value, table)
}


/**
 * Base interface for an identity table which could be referenced from another tables.
 *
 */
interface IdTableInterface<T:Comparable<T>>: ITable {
    val id : Column<EntityID<T>>
}


/**
 * Base class for an identity table which could be referenced from another tables.
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
abstract class IdTable<T:Comparable<T>>(name: String = ""): Table(name), IdTableInterface<T> {
    abstract override val id : Column<EntityID<T>>
}

/**
 * Identity table with autoincrement integer primary key
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 * @param columnName name for a primary key, "id" by default
 */
open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name), IntIdTableInterface {
    override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
}

/**
 * Identity table with autoincrement long primary key
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 * @param columnName name for a primary key, "id" by default
 */
open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name), LongIdTableInterface {
    override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
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
open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name), UUIDTableInterface {
    override val id: Column<EntityID<UUID>> = uuid(columnName)
            .autoGenerate()
            .entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
}
