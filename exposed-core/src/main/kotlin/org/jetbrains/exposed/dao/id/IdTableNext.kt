package org.jetbrains.exposed.dao.id

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.PrimaryKey
import org.jetbrains.exposed.sql.Table
import java.util.*


/**
 * Base class for an every ITable table that use entity id.
 *
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
internal class NextIdTableBase<T:Comparable<T>>(name: String = ""): Table(name),
    IdTableInterface<T> {
    override lateinit var id : Column<EntityID<T>> // is overrided and is not called.
}

fun <T:Comparable<T>>createIdTable(name: String): IdTableInterface<T> = NextIdTableBase(name)

/**
 * Identity table with autoincrement integer primary key
 *
 * @param columnName name for a primary key, "id" by default
 * @param table parent table that will manager the table.
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
open class NextIntIdTable(table: IdTableInterface<Int>, columnName: String = "id"): IdTableInterface<Int> by table, IntIdTableInterface {
    constructor(name: String = "", columnName: String = "id"): this(createIdTable<Int>(name), columnName)
    override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().entityId()
    override val primaryKey by lazy { PrimaryKey(id, table=this) }
}


/**
 * Identity table with autoincrement long primary key
 *
 * @param columnName name for a primary key, "id" by default
 * @param table parent table that will manager the table.
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
open class NextLongIdTable(table: IdTableInterface<Long>, columnName: String = "id"): IdTableInterface<Long> by table, LongIdTableInterface {
    constructor(name: String = "", columnName: String = "id"): this(createIdTable<Long>(name), columnName)
    override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().entityId()
    override val primaryKey by lazy { PrimaryKey(id, table=this) }
}

/**
 * Identity table with autoincrement uuid primary key
 *
 * @param columnName name for a primary key, "id" by default
 * @param table parent table that will manager the table.
 * @param name table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
open class NextUUIDTable(table: IdTableInterface<UUID>, columnName: String = "id"): IdTableInterface<UUID> by table, UUIDTableInterface {
    constructor(name: String = "", columnName: String = "id"): this(createIdTable<UUID>(name), columnName)
    override val id: Column<EntityID<UUID>> = uuid(columnName).autoGenerate().entityId()
    override val primaryKey by lazy { PrimaryKey(id, table=this) }
}