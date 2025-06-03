package org.example

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.r2dbc.addLogger
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun main() {
    val h2db = R2DBCDatabases().getH2DB()
    suspendTransaction(db = h2db) {
        // DSL/DAO operations go here
        addLogger(StdOutSqlLogger)
    }
}
