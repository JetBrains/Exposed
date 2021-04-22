package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

interface TransactionInterface {

    val db: Database

    val connection: ExposedConnection<*>

    val transactionIsolation: Int

    val readOnly: Boolean

    val outerTransaction: Transaction?

    fun commit()

    fun rollback()

    fun close()
}

const val DEFAULT_ISOLATION_LEVEL = Connection.TRANSACTION_REPEATABLE_READ

const val DEFAULT_READ_ONLY = false

const val DEFAULT_REPETITION_ATTEMPTS = 3

private object NotInitializedManager : TransactionManager {
    override var defaultIsolationLevel: Int = -1

    override var defaultReadOnly: Boolean = false

    override var defaultRepetitionAttempts: Int = -1

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): Transaction = error("Please call Database.connect() before using this code")

    override fun currentOrNull(): Transaction? = error("Please call Database.connect() before using this code")

    override fun bindTransactionToThread(transaction: Transaction?) {
        error("Please call Database.connect() before using this code")
    }
}

interface TransactionManager {

    var defaultIsolationLevel: Int

    var defaultReadOnly: Boolean

    var defaultRepetitionAttempts: Int

    fun newTransaction(
        isolation: Int = defaultIsolationLevel,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: Transaction? = null
    ): Transaction

    fun currentOrNull(): Transaction?

    fun bindTransactionToThread(transaction: Transaction?)

    companion object {
        private val currentDefaultDatabase = AtomicReference<Database>()

        var defaultDatabase: Database?
            get() = currentDefaultDatabase.get() ?: databases.firstOrNull()
            set(value) { currentDefaultDatabase.set(value) }

        private val databases = ConcurrentLinkedDeque<Database>()

        private val registeredDatabases = ConcurrentHashMap<Database, TransactionManager>()

        fun registerManager(database: Database, manager: TransactionManager) {
            if (defaultDatabase == null) {
                currentThreadManager.remove()
            }
            registeredDatabases[database] = manager
            databases.push(database)
        }

        fun closeAndUnregister(database: Database) {
            val manager = registeredDatabases[database]
            manager?.let {
                registeredDatabases.remove(database)
                databases.remove(database)
                currentDefaultDatabase.compareAndSet(database, null)
                if (currentThreadManager.get() == it)
                    currentThreadManager.remove()
            }
        }

        fun managerFor(database: Database?) = if (database != null) registeredDatabases[database] else manager

        internal val currentThreadManager = object : ThreadLocal<TransactionManager>() {
            override fun initialValue(): TransactionManager {
                return defaultDatabase?.let { registeredDatabases.getValue(it) } ?: NotInitializedManager
            }
        }

        val manager: TransactionManager
            get() = currentThreadManager.get()

        fun resetCurrent(manager: TransactionManager?) {
            manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
        }

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull() = manager.currentOrNull()

        fun current() = currentOrNull() ?: error("No transaction in context.")

        fun isInitialized() = defaultDatabase != null
    }
}

internal fun TransactionInterface.rollbackLoggingException(log: (Exception) -> Unit) {
    try {
        rollback()
    } catch (e: Exception) {
        log(e)
    }
}

internal inline fun TransactionInterface.closeLoggingException(log: (Exception) -> Unit) {
    try {
        close()
    } catch (e: Exception) {
        log(e)
    }
}

val Database?.transactionManager: TransactionManager
    get() = TransactionManager.managerFor(this) ?: throw RuntimeException("database $this don't have any transaction manager")
