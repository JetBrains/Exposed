package org.example

import org.example.examples.*
import org.jetbrains.exposed.v1.Database
import org.jetbrains.exposed.v1.SchemaUtils
import org.jetbrains.exposed.v1.addLogger
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.transactions.transaction

val jsonExamples = JSONandJSONBExamples()
val enumExamples = EnumerationExamples()
val binaryExamples = BinaryExamples()
val dateTimeExamples = DateTimeExamples()

fun main() {
//    runMySQLExamples()
    runPostgreSQLExamples()
    runH2Examples()
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
        SchemaUtils.create(DateTimeExamples.Events)
        jsonExamples.example()
        jsonExamples.useExtract()
        jsonExamples.useContains()
        jsonExamples.useContainsWithPath()
        jsonArraysExamples()
//        dateTimeExamples.dateExample()
//        dateTimeExamples.timeExample()
//        dateTimeExamples.datetimeExample()
//        dateTimeExamples.timestampExample()
        binaryExamples.basicUsage()
//        binaryExamples.parameterBinding()
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
        SchemaUtils.create(DateTimeExamples.Events)
        jsonExamples.example()
        enumExamples.createTableWithExistingEnumColumn()
        enumExamples.insertEnumIntoTableWithExistingEnumColumn()
        dateTimeExamples.dateExample()
        dateTimeExamples.timeExample()
        dateTimeExamples.datetimeExample()
        dateTimeExamples.timestampExample()
        binaryExamples.basicUsage()
        binaryExamples.parameterBinding()
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
        SchemaUtils.create(TeamsTable)
        SchemaUtils.create(TeamProjectsTable)
        SchemaUtils.create(DateTimeExamples.Events)
//        enumExamples.createTableWithEnumColumn()
        binaryExamples.basicUsage()
        binaryExamples.parameterBinding()
        dateTimeExamples.dateExample()
        dateTimeExamples.timeExample()
        dateTimeExamples.datetimeExample()
        dateTimeExamples.timestampExample()
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
