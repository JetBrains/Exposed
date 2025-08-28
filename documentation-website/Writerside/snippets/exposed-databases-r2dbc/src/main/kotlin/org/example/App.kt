@file:Suppress("MagicNumber", "UnusedPrivateProperty")

package org.example

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun main() {
    val db = R2DBCDatabases().getPostgreSQLDB()
    val jamesList = suspendTransaction(db = db) {
        UsersTable.selectAll().where { UsersTable.firstName eq "James" }.toList()
    }
    println(jamesList)
}

suspend fun openTransactionWithParams() {
    val h2Db = R2DBCDatabases().getH2DB()

    suspendTransaction(
        db = h2Db,
        transactionIsolation = IsolationLevel.READ_COMMITTED
    ) {
        maxAttempts = 5
        queryTimeout = 5

        // run database commands
    }
}

suspend fun createTable() {
    suspendTransaction {
        // DSL/DAO operations go here
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(UsersTable)
        UsersTable.insert {
            it[firstName] = "James"
        }
    }
}
