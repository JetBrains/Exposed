package org.example

import org.example.examples.*
import org.example.tables.*
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig

suspend fun main() {
    R2dbcDatabase.connect(
        url = "r2dbc:h2:mem:///test",
        databaseConfig = R2dbcDatabaseConfig { useNestedTransactions = true }
    )

    suspendTransaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(ActorsTable)
        SchemaUtils.create(StarWarsFilmsTable)
        SchemaUtils.create(StarWarsFilmActorsTable)
        SchemaUtils.create(UserRatingsTable)
        SchemaUtils.create(UsersTable)
        runOneToManyExample()
        runManyToManyExample()
        runParentChildExample()
        runEagerLoadingExamples()
    }
}

fun runOneToManyExample() {
    val oneToManyExamples = OneToManyExamples()
    oneToManyExamples.queryRatings()
}

suspend fun runManyToManyExample() {
    val manyToManyExamples = ManyToManyExamples()
    manyToManyExamples.getActors()
}

suspend fun runParentChildExample() {
    // create tables
    SchemaUtils.create(DirectorsTable)
    SchemaUtils.create(StarWarsFilmsWithDirectorTable)
    SchemaUtils.create(StarWarsFilmRelationsTable)

    val parentChildExamples = ParentChildExamples()
    parentChildExamples.querySequels()
}

suspend fun runEagerLoadingExamples() {
    val eagerLoadingExamples = EagerLoadingExamples()
    eagerLoadingExamples.load()
}
