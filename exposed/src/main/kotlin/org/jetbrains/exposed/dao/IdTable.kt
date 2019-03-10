package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

abstract class IdTable<T:Comparable<T>>(name: String=""): Table(name) {
    abstract val id : Column<EntityID<T>>

}
