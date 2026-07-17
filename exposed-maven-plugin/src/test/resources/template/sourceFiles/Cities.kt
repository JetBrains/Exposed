package com.example.tables

import org.jetbrains.exposed.v1.core.Table

private const val NAME_LENGTH = 255

object Cities : Table("cities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", NAME_LENGTH)

    override val primaryKey = PrimaryKey(id)
}
