package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import java.sql.Connection

interface TransactionInterface {

    val db : Database

    val connection: Connection

    val outerTransaction: Transaction?

    fun commit()

    fun rollback()

    fun close()
}

interface TransactionManager {

    fun newTransaction(isolation: Int = Connection.TRANSACTION_REPEATABLE_READ) : Transaction

    fun currentOrNull(): Transaction?

    companion object {
        internal lateinit var manager: TransactionManager

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull() = manager.currentOrNull()

        fun current(): Transaction = currentOrNull() ?: error("No transaction in context.")
    }
}