package org.example

import org.example.examples.*
import org.example.tables.*
import org.jetbrains.exposed.v1.sql.Database
import org.jetbrains.exposed.v1.sql.DatabaseConfig
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.StdOutSqlLogger
import org.jetbrains.exposed.v1.sql.addLogger
import org.jetbrains.exposed.v1.sql.transactions.transaction
import java.util.*

fun main() {
    Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    transaction {
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

fun runManyToManyExample() {
    val manyToManyExamples = ManyToManyExamples()
    manyToManyExamples.getActors()
}

fun runParentChildExample() {
    // create tables
    SchemaUtils.create(DirectorsTable)
    SchemaUtils.create(StarWarsFilmsWithDirectorTable)
    SchemaUtils.create(StarWarsFilmRelationsTable)

    val parentChildExamples = ParentChildExamples()
    parentChildExamples.querySequels()
}

fun runEagerLoadingExamples() {
    val eagerLoadingExamples = EagerLoadingExamples()
    eagerLoadingExamples.load()
}
