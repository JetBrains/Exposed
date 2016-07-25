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

        @Volatile lateinit internal var _manager: TransactionManager

        var currentThreadManager = object : ThreadLocal<TransactionManager>() {
            override fun initialValue(): TransactionManager = _manager
        }

        fun currentOrNew(isolation: Int) = currentOrNull() ?: currentThreadManager.get().newTransaction(isolation)

        fun currentOrNull() = currentThreadManager.get().currentOrNull()

        fun current() = currentOrNull() ?: error("No transaction in context.")
    }
}