package com.example.tables

import org.jetbrains.exposed.v1.core.Table

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val email = varchar("email", 255)

    override val primaryKey = PrimaryKey(id)
}
