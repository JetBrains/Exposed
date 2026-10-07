package org.example

import org.example.examples.*
import org.example.tables.*
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

val updateExamples = UpdateExamples()

suspend fun main() {
    R2dbcDatabase.connect(
        url = "r2dbc:h2:mem:///test",
        databaseConfig = R2dbcDatabaseConfig { useNestedTransactions = true }
    )

    suspendTransaction {
        addLogger(StdOutSqlLogger)
        createTables()
        runCreateExamples()
        runReadExamples()
        runUpdateExamples()
        runDeleteExamples()
        updateExamples.updateInNewTransaction()
    }
}

suspend fun createTables() {
    SchemaUtils.create(StarWarsFilmsTable)
    SchemaUtils.create(DirectorsTable)
    SchemaUtils.create(UsersTable)
    SchemaUtils.create(UserRatingsTable)
    SchemaUtils.create(GuildsTable)
    SchemaUtils.create(CitiesTable)
    SchemaUtils.create(StarWarsFilmsWithRankTable)
}

suspend fun runCreateExamples() {
    val createExamples = CreateExamples()
    createExamples.createFilms()
    createExamples.createNewWithCompositeId()
    createExamples.createScheduledFilm()
}

suspend fun runReadExamples() {
    val readExamples = ReadExamples()
    readExamples.readAll()
    readExamples.readWithJoin()
    readExamples.find()
    readExamples.findByCompositeId()
    readExamples.queriesAsExpressions()
    readExamples.readComputedField()
}

suspend fun runUpdateExamples() {
    updateExamples.updateFilms()
    updateExamples.updateFilmProperty()
}

suspend fun runDeleteExamples() {
    val deleteExamples = DeleteExamples()
    deleteExamples.deleteFilm()
}
