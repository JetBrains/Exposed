package com.example.tables

import org.jetbrains.exposed.v1.core.Table

object Cities : Table("cities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}
