package org.example

import CreateExamples
import org.example.tables.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    transaction {
        addLogger(StdOutSqlLogger)
        createTables()
        runCreateExamples()
    }
}

fun createTables() {
    SchemaUtils.create(StarWarsFilmsTable)
    SchemaUtils.create(CitiesTable)
}

fun runCreateExamples() {
    val createExamples = CreateExamples()
    createExamples.createFilms()
}
