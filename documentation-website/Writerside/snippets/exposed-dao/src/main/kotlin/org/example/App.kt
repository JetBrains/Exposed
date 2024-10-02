package org.example

import org.example.examples.*
import org.example.tables.CitiesTable
import org.example.tables.DirectorsTable
import org.example.tables.StarWarsFilmsTable
import org.example.tables.UserRatingsTable
import org.example.tables.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(StarWarsFilmsTable)
        SchemaUtils.create(DirectorsTable)
        SchemaUtils.create(UsersTable)
        SchemaUtils.create(UserRatingsTable)
        SchemaUtils.create(CitiesTable)

        // Create examples
        val createExamples = CreateExamples()
        createExamples.createFilms()

        // Read examples
        val readExamples = ReadExamples()
        readExamples.readExamples()
    }
}
