package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger

/**
 * Wrapper is used to separate ThreadLocalTransactionManager between different context classloaders
 */
class TransactionManagerContextWrapper(database: Database, override var defaultIsolationLevel: Int) : TransactionManager {

    private val contextClassLoader get() = Thread.currentThread().contextClassLoader

    init {
        ContextManagers[contextClassLoader, database] = defaultIsolationLevel
    }

    companion object ContextManagers {
        private val managers = hashMapOf<ClassLoader, ThreadLocalTransactionManager>()

        operator fun set(classLoader: ClassLoader, database: Database, isolationLevel: Int) {
            exposedLogger.info("Registering database for $classLoader")
            managers.put(classLoader, ThreadLocalTransactionManager(database, isolationLevel))
            exposedLogger.info("Total registered: ${managers.size}")
        }

        operator fun get(classLoader: ClassLoader) = managers[classLoader] ?: error("Transaction manager is not set for $classLoader")
    }

    override fun currentOrNull(): Transaction? = ContextManagers[contextClassLoader].currentOrNull()

    override fun newTransaction(isolation: Int) = ContextManagers[contextClassLoader].newTransaction(isolation)
}