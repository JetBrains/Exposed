package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Table

open class IdTable(name: String = ""): Table(name) {
    val id = entityId("id", this).autoinc().primaryKey()
}
