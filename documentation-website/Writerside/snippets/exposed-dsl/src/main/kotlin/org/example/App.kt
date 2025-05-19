package org.example

import org.example.examples.AliasExamples
import org.example.examples.CreateExamples
import org.example.examples.CustomSelectExamples
import org.example.examples.DeleteExamples
import org.example.examples.QueryingExamples
import org.example.examples.ReadExamples
import org.example.examples.UpdateExamples
import org.example.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun main() {
    val h2Db = Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    val sqliteDb = Database.connect(
        "jdbc:sqlite:file:test?mode=memory&cache=shared",
        "org.sqlite.JDBC",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    val mysqlDb = Database.connect(
        "jdbc:mysql://localhost:3306/test",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = "password",
    )

    transaction(h2Db) {
        addLogger(StdOutSqlLogger)
        createTables()
        runCreateExamples()
        runReadExamples()
        runUpdateExamples()
        runQueryingExamples()
        runAliasExamples()
        runDeleteExamples()
    }

    transaction(sqliteDb) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(StarWarsFilmsIntIdTable)
        // run examples for insertIgnore and insertIgnoreAndGetId
        CreateExamples().insertIgnoreRecords()
    }

    transaction(mysqlDb) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(StarWarsFilmsIntIdTable)
        SchemaUtils.create(ActorsIntIdTable)
        runCustomSelectExamples()
        // run examples for deleteIgnoreWhere
        DeleteExamples().deleteIgnore()
        // run examples for joinDelete and joinUpdate
        DeleteExamples().joinDelete()
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
    deleteExamples.deleteAll()
}

fun runQueryingExamples() {
    val queryingExamples = QueryingExamples()
    queryingExamples.useWhereConditions()
}

fun runAliasExamples() {
    val aliasExamples = AliasExamples()
    aliasExamples.useAlias()
}

fun runCustomSelectExamples() {
    val customSelectExamples = CustomSelectExamples()
    customSelectExamples.useCustomQueryWithHint()
}
