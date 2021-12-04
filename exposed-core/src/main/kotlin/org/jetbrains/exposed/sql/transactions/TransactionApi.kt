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

    val outerTransaction: Transaction?

    fun commit()

    fun rollback()

    fun close()
}

const val DEFAULT_ISOLATION_LEVEL = Connection.TRANSACTION_REPEATABLE_READ

private object NotInitializedManager : TransactionManager {
    override var defaultIsolationLevel: Int = -1

    override var defaultRepetitionAttempts: Int = -1

    override fun newTransaction(isolation: Int, outerTransaction: Transaction?): Transaction =
        error("Please call Database.connect() before using this code")

    override fun currentOrNull(): Transaction = error("Please call Database.connect() before using this code")

    override fun bindTransactionToThread(transaction: Transaction?) {
        error("Please call Database.connect() before using this code")
    }
}

interface TransactionManager {

    var defaultIsolationLevel: Int

    var defaultRepetitionAttempts: Int

    fun newTransaction(isolation: Int = defaultIsolationLevel, outerTransaction: Transaction? = null): Transaction

    fun currentOrNull(): Transaction?

    fun bindTransactionToThread(transaction: Transaction?)

    companion object {
        internal val currentDefaultDatabase = AtomicReference<Database>()

        var defaultDatabase: Database?
            @Synchronized get() = currentDefaultDatabase.get() ?: databases.firstOrNull()
            @Synchronized set(value) { currentDefaultDatabase.set(value) }

        private val databases = ConcurrentLinkedDeque<Database>()

        private val registeredDatabases = ConcurrentHashMap<Database, TransactionManager>()

        @Synchronized fun registerManager(database: Database, manager: TransactionManager) {
            if (defaultDatabase == null) {
                currentThreadManager.remove()
            }
            if (!registeredDatabases.containsKey(database)) {
                databases.push(database)
            }

            registeredDatabases[database] = manager
        }

        @Synchronized fun closeAndUnregister(database: Database) {
            val manager = registeredDatabases[database]
            manager?.let {
                registeredDatabases.remove(database)
                databases.remove(database)
                currentDefaultDatabase.compareAndSet(database, null)
                if (currentThreadManager.get() == it) {
                    currentThreadManager.remove()
                }
            }
        }

        fun managerFor(database: Database?) = if (database != null) registeredDatabases[database] else manager

        private val currentThreadManager = object : ThreadLocal<TransactionManager>() {
            override fun initialValue(): TransactionManager {
                return defaultDatabase?.let { registeredDatabases.getValue(it) } ?: NotInitializedManager
            }
        }

        val manager: TransactionManager
            get() = currentThreadManager.get()

        fun resetCurrent(manager: TransactionManager?) {
            manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
        }

        fun currentOrNew(isolation: Int): Transaction = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull(): Transaction? = manager.currentOrNull()

        fun current(): Transaction = currentOrNull() ?: error("No transaction in context.")

        fun isInitialized(): Boolean = defaultDatabase != null
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
