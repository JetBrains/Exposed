package org.example

import org.example.examples.*
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
        runReadExamples()
        runUpdateExamples()
        runDeleteExamples()
    }
}

fun createTables() {
    SchemaUtils.create(StarWarsFilmsTable)
    SchemaUtils.create(DirectorsTable)
    SchemaUtils.create(UsersTable)
    SchemaUtils.create(UserRatingsTable)
    SchemaUtils.create(GuildsTable)
    SchemaUtils.create(CitiesTable)
    SchemaUtils.create(StarWarsFilmsWithRankTable)
}

fun runCreateExamples() {
    val createExamples = CreateExamples()
    createExamples.createFilms()
    createExamples.createNewWithCompositeId()
}

fun runReadExamples() {
    val readExamples = ReadExamples()
    readExamples.readAll()
    readExamples.readWithJoin()
    readExamples.find()
    readExamples.findByCompositeId()
    readExamples.queriesAsExpressions()
    readExamples.readComputedField()
}

fun runUpdateExamples() {
    val updateExamples = UpdateExamples()
    updateExamples.updateFilms()
    updateExamples.updateFilmProperty()
}

fun runDeleteExamples() {
    val deleteExamples = DeleteExamples()
    deleteExamples.deleteFilm()
}
