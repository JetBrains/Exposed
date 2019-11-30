package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*

@Deprecated(
        message = "Package was changed please re-import",
        replaceWith = ReplaceWith("org.jetbrains.exposed.dao.id.EntityID<T>"),
        level = DeprecationLevel.WARNING
)
open class EntityID<T:Comparable<T>> protected constructor(val table: IdTable<T>, id: T?) : Comparable<EntityID<T>> {
    constructor(id:T, table: IdTable<T>) : this(table, id)
    var _value: Any? = id
    val value: T get() {
        if (_value == null) {
            invokeOnNoValue()
            check(_value != null) { "Entity must be inserted" }
        }

        @Suppress("UNCHECKED_CAST")
        return _value!! as T
    }

    protected open fun invokeOnNoValue() {}

    override fun toString() = value.toString()

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is EntityID<*>) return false

        return other._value == _value && other.table == table
    }

    override fun compareTo(other: EntityID<T>): Int = value.compareTo(other.value)
}

@Deprecated(
        message = "Package was changed please re-import",
        replaceWith = ReplaceWith("org.jetbrains.exposed.dao.id.IdTable<T>"),
        level = DeprecationLevel.HIDDEN
)
abstract class IdTable<T:Comparable<T>>(name: String = ""): Table(name) {
    abstract val id : Column<org.jetbrains.exposed.dao.id.EntityID<T>>
}

@Deprecated(
        message = "Package was changed please re-import",
        replaceWith = ReplaceWith("org.jetbrains.exposed.dao.id.IntIdTable"),
        level = DeprecationLevel.HIDDEN
)
open class IntIdTable(name: String = "", columnName: String = "id")
    : org.jetbrains.exposed.dao.id.IdTable<Int>(name) {
    override val id = integer(columnName).autoIncrement().primaryKey().entityId()
}

@Deprecated(
        message = "Package was changed please re-import",
        replaceWith = ReplaceWith("org.jetbrains.exposed.dao.id.LongIdTable"),
        level = DeprecationLevel.WARNING
)
open class LongIdTable(name: String = "", columnName: String = "id") : org.jetbrains.exposed.dao.id.IdTable<Long>(name) {
    override val id = long(columnName).autoIncrement().primaryKey().entityId()
}

@Deprecated(
        message = "Package was changed please re-import",
        replaceWith = ReplaceWith("org.jetbrains.exposed.dao.id.UUIDTable"),
        level = DeprecationLevel.WARNING
)
open class UUIDTable(name: String = "", columnName: String = "id") : org.jetbrains.exposed.dao.id.IdTable<UUID>(name) {
    override val id = uuid(columnName).primaryKey().autoGenerate().entityId()
}
