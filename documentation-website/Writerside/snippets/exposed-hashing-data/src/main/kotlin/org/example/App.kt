package org.example

import org.example.tables.Users
import org.example.tables.hasher
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/*
    Important: The contents of this file are referenced by line number in `hashing-data.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

fun main() {
    val h2db = Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    transaction(h2db) {
        SchemaUtils.create(Users)
        Users.insert {
            it[email] = "john@mail.com"
            it[password] = hasher.hash("s3cret")
        }
        val user = Users.selectAll().where {
            Users.email eq "john@mail.com"
        }.single()

        if (user[Users.password].matches("s3cret")) {
            println("Passwords match.")
        }
    }
}
