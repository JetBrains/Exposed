package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

suspend fun <T> transaction(db: Database? = null, statement: suspend Transaction.() -> T): T =
        transaction(TransactionManager.manager.defaultIsolationLevel, TransactionManager.manager.defaultRepetitionAttempts, db, statement)

suspend fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: suspend Transaction.() -> T): T =
    org.jetbrains.exposed.sql.transactions.transaction(transactionIsolation, repetitionAttempts, db) {
        runBlocking { statement() }
    }
