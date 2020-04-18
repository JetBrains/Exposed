package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

interface ITransactionManager {

    var defaultIsolationLevel: Int

    var defaultRepetitionAttempts: Int

    fun newTransaction(isolation: Int = defaultIsolationLevel, outerTransaction: ITransaction? = null) : ITransaction

    fun currentOrNull(): ITransaction?

    companion object {

        val managers = ConcurrentLinkedDeque<ITransactionManager>().apply {
            push(NotInitializedManager)
        }

        private val registeredDatabases = ConcurrentHashMap<Database, ITransactionManager>()

        fun registerManager(database: Database, manager: ITransactionManager) {
            registeredDatabases[database] = manager
            managers.push(manager)
        }

        fun closeAndUnregister(database: Database) {
            val manager = registeredDatabases[database]
            manager?.let {
                registeredDatabases.remove(database)
                managers.remove(it)
                if (currentThreadManager.get() == it)
                    currentThreadManager.remove()
            }
        }

        fun managerFor(database: Database?) = if (database != null) registeredDatabases[database] else manager

        val currentThreadManager = object : ThreadLocal<ITransactionManager>() {
            override fun initialValue(): ITransactionManager = managers.first
        }

        val manager: ITransactionManager
            get() = currentThreadManager.get()


        fun resetCurrent(manager: ITransactionManager?)  {
            manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
        }

        fun currentOrNew(isolation: Int) = currentOrNull() ?: manager.newTransaction(isolation)

        fun currentOrNull() = manager.currentOrNull()

        fun current() = currentOrNull() ?: error("No transaction in context.")

        fun isInitialized() = managers.first != NotInitializedManager
    }
}

val Database?.transactionManager: ITransactionManager get() = ITransactionManager.managerFor(this) ?: throw RuntimeException("database ${this} don't have any transaction manager")

private object NotInitializedManager : ITransactionManager {
    override var defaultIsolationLevel: Int = -1

    override var defaultRepetitionAttempts: Int = -1

    override fun newTransaction(isolation: Int, outerTransaction: ITransaction?): ITransaction = error("Please call Database.connect() before using this code")

    override fun currentOrNull(): ITransaction? = error("Please call Database.connect() before using this code")
}
