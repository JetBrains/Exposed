package org.example

import org.example.examples.AggregateFuncExamples
import org.example.examples.CustomFuncExamples
import org.example.examples.StringFuncExamples
import org.example.examples.WindowFuncExamples
import org.example.tables.FilmBoxOfficeTable
import org.jetbrains.exposed.v1.sql.Database
import org.jetbrains.exposed.v1.sql.DatabaseConfig
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.StdOutSqlLogger
import org.jetbrains.exposed.v1.sql.addLogger
import org.jetbrains.exposed.v1.sql.transactions.transaction

fun main() {
    Database.connect(
        "jdbc:h2:mem:test",
        "org.h2.Driver",
        databaseConfig = DatabaseConfig { useNestedTransactions = true }
    )

    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(FilmBoxOfficeTable)
        runStringFuncExamples()
        runAggregateFuncExamples()
        runWindowFuncExamples()
        runCustomFuncExamples()
    }
}

fun runStringFuncExamples() {
    val stringFuncExamples = StringFuncExamples()
    stringFuncExamples.selectStringFunctions()
}

fun runAggregateFuncExamples() {
    val aggregateFuncExamples = AggregateFuncExamples()
    aggregateFuncExamples.selectAggregateFunctions()
}

fun runWindowFuncExamples() {
    val windowFuncExamples = WindowFuncExamples()
    windowFuncExamples.selectWindowFunctions()
}

fun runCustomFuncExamples() {
    val customFuncExamples = CustomFuncExamples()
    customFuncExamples.selectCustomFunctions()
    customFuncExamples.selectCustomTrimFunction()
}
