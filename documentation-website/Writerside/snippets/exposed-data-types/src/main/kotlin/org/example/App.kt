package org.example

import org.example.examples.EnumerationExamples
import org.example.examples.JSONandJSONBExamples
import org.example.examples.TeamsTable
import org.example.examples.TeamProjectsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

val jsonExamples = JSONandJSONBExamples()
val enumExamples = EnumerationExamples()

fun main() {
    runMySQLExamples()
//    runPostgreSQLExamples()
//    runH2Examples()
}

fun runMySQLExamples() {
    val mysqlDb = Database.connect(
        "jdbc:mysql://localhost:3306/test?allowMultiQueries=true",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = "password",
    )
    transaction(mysqlDb) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(TeamsTable)
        SchemaUtils.create(TeamProjectsTable)
        jsonExamples.example()
        jsonExamples.useExtract()
        jsonExamples.useContains()
        jsonExamples.useContainsWithPath()
        jsonArraysExamples()
        enumExamples.createTableWithExistingEnumColumn()
    }
}

fun runH2Examples() {
    val h2Db = Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    transaction(h2Db) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(TeamsTable)
        jsonExamples.example()
        jsonExamples.useExtract()
        jsonExamples.useExists()
    }
}

fun runPostgreSQLExamples() {
    val postgreSQL = Database.connect(
        "jdbc:postgresql://localhost:5432/postgres",
        driver = "org.postgresql.Driver",
        user = "user",
        password = "password"
    )

    transaction(postgreSQL) {
        addLogger(StdOutSqlLogger)
        enumExamples.createTableWithEnumColumn()
    }
}

fun runJSONandJSONBExamples() {
    jsonExamples.example()
    jsonExamples.useExtract()
}

fun jsonArraysExamples() {
    SchemaUtils.create(TeamProjectsTable)
    jsonExamples.insertJSONArrays()
}

