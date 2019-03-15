package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

interface TransactionInterface {

    val db : Database

    val connection: Connection

    val outerTransaction: Transaction?

    fun commit()

    fun rollback()

    fun close()
}

const val DEFAULT_ISOLATION_LEVEL = Connection.TRANSACTION_REPEATABLE_READ

const val DEFAULT_REPETITION_ATTEMPTS = 3

private object NotInitializedManager : TransactionManager {
    override var defaultIsolationLevel: Int = -1

    override var defaultRepetitionAttempts: Int = -1

    override fun newTransaction(isolation: Int): Transaction = error("Please call Database.connect() before using this code")

    override fun currentOrNull(): Transaction? = error("Please call Database.connect() before using this code")
}

interface TransactionManager {

    var defaultIsolationLevel: Int

    var defaultRepetitionAttempts: Int

    fun newTransaction(isolation: Int = defaultIsolationLevel) : Transaction

    fun currentOrNull(): Transaction?

    companion object {

        @Volatile private var _manager: TransactionManager = NotInitializedManager

        private val registeredDatabases = ConcurrentHashMap<Database, TransactionManager>()

        fun registerManager(database: Database, manager: TransactionManager) {
            registeredDatabases[database] = manager
            this._manager = manager
        }

        internal fun managerFor(database: Database) = registeredDatabases[database]

        private val currentThreadManager = object : ThreadLocal<TransactionManager>() {
            override fun initialValue(): TransactionManager = _manager
        }

        val manager: TransactionManager
            get() = currentThreadManager.get()


        fun resetCurrent(manager: TransactionManager?)  {
            manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
        }

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull() = manager.currentOrNull()

        fun current() = currentOrNull() ?: error("No transaction in context.")

        fun isInitialized() = _manager != NotInitializedManager
    }
}