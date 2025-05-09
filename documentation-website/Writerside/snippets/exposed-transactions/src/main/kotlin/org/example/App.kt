package org.example

import org.example.examples.ExecExamples
import org.example.examples.ExecMySQLExamples
import org.example.examples.SavepointExample
import org.example.tables.FilmsTable
import org.jetbrains.exposed.v1.Database
import org.jetbrains.exposed.v1.SchemaUtils
import org.jetbrains.exposed.v1.addLogger
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.transactions.transaction

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
        runSavepointExample()
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

fun runSavepointExample() {
    val savepointExample = SavepointExample()
    savepointExample.setSavepoint()
    savepointExample.nestedTransaction()
}
