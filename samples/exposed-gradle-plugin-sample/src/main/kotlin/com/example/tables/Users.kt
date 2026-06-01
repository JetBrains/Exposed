package com.example.tables

import org.jetbrains.exposed.v1.core.Table

private const val NAME_LENGTH = 255
private const val EMAIL_LENGTH = 255

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", NAME_LENGTH)
    val email = varchar("email", EMAIL_LENGTH)

    override val primaryKey = PrimaryKey(id)
}
