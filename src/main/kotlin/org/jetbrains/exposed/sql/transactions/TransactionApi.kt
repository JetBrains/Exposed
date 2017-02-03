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

const val DEFAULT_ISOLATION_LEVEL = Connection.TRANSACTION_REPEATABLE_READ

interface TransactionManager {

    var defaultIsolationLevel: Int

    fun newTransaction(isolation: Int = defaultIsolationLevel) : Transaction

    fun currentOrNull(): Transaction?

    companion object {

        @Volatile lateinit private var _manager: TransactionManager

        internal var currentThreadManager = object : ThreadLocal<TransactionManager>() {
            override fun initialValue(): TransactionManager = _manager
        }

        var manager: TransactionManager
            get() = currentThreadManager.get()
            set(value) {
                _manager = value
                removeCurrent()
            }

        fun removeCurrent() = currentThreadManager.remove()

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull() = manager.currentOrNull()

        fun current() = currentOrNull() ?: error("No transaction in context.")
    }
}