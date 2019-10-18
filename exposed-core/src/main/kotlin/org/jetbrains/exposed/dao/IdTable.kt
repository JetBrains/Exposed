package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*

interface EntityIDFactory {
    fun <T:Comparable<T>> createEntityID(value: T, table: IdTable<T>) : EntityID<T>
}

object EntityIDFunctionProvider {
    var factory : EntityIDFactory = object : EntityIDFactory {
        override fun <T : Comparable<T>> createEntityID(value: T, table: IdTable<T>): EntityID<T> {
            return SimpleEntityID(value, table)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Comparable<T>> createEntityID(value: T, table: IdTable<T>) = factory.createEntityID(value, table)
}

abstract class IdTable<T:Comparable<T>>(name: String = ""): Table(name) {
    abstract val id : Column<EntityID<T>>
}

open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name) {
    override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().primaryKey().entityId()
}

open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name) {
    override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().primaryKey().entityId()
}

open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    override val id: Column<EntityID<UUID>> = uuid(columnName).primaryKey()
            .clientDefault { UUID.randomUUID() }
            .entityId()
}
