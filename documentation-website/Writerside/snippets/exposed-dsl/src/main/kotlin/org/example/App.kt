package org.example

import CreateExamples
import org.example.examples.DeleteExamples
import org.example.examples.QueryingExamples
import org.example.examples.ReadExamples
import org.example.examples.UpdateExamples
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
        //runQueryingExamples()
    }
}

fun createTables() {
    val tables = listOf(
        StarWarsFilmsTable,
        StarWarsFilmsIntIdTable,
        CitiesTable,
        ActorsTable,
        CitiesTable,
        UsersTable
    )

    tables.forEach { table ->
        SchemaUtils.create(table)
    }

}

fun runCreateExamples() {
    val createExamples = CreateExamples()
    createExamples.createFilmRecords()
    createExamples.createIntIdFilmRecords()
    createExamples.simpleBatchInsert()
    createExamples.batchInsert()
}

fun runReadExamples() {
    val readExamples = ReadExamples()
    readExamples.read()
    readExamples.readAll()
}

fun runUpdateExamples() {
    val updateExamples = UpdateExamples()
    updateExamples.updateRecords()
}

fun runDeleteExamples() {
    val deleteExamples = DeleteExamples()
    deleteExamples.delete()
    deleteExamples.joinDelete()
    deleteExamples.deleteAll()
}

fun runQueryingExamples() {
    val queryingExamples = QueryingExamples()
    queryingExamples.printResults()
}
