package org.example

import org.example.examples.ExecExamples
import org.example.examples.ExecMySQLExamples
import org.example.tables.FilmsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    val h2Db = Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    val mysqlDb = Database.connect(
        "jdbc:mysql://localhost:3306/test?allowMultiQueries=true",
        driver = "com.mysql.cj.jdbc.Driver",
        user = "root",
        password = "password",
    )

    transaction(h2Db) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(FilmsTable)
        runExecExamples()
    }

    transaction(mysqlDb) {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(FilmsTable)
        runExecMySQLExamples()
    }
}

fun runExecExamples() {
    val execExamples = ExecExamples()
    execExamples.execBasicStrings()
    execExamples.execAndMapResult()
    execExamples.execWithParameters()
    execExamples.execWithTypeOverride()
}

fun runExecMySQLExamples() {
    val execMySQLExamples = ExecMySQLExamples()
    execMySQLExamples.execMultipleStrings()
}
