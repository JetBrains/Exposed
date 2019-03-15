package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import java.sql.Connection
import java.sql.SQLException

class ThreadLocalTransactionManager(private val db: Database,
                                    @Volatile override var defaultIsolationLevel: Int,
                                    @Volatile override var defaultRepetitionAttempts: Int) : TransactionManager {

    val threadLocal = ThreadLocal<Transaction>()

    override fun newTransaction(isolation: Int): Transaction = Transaction(ThreadLocalTransaction(db, isolation, threadLocal)).apply {
        threadLocal.set(this)
    }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    private class ThreadLocalTransaction(override val db: Database, isolation: Int, val threadLocal: ThreadLocal<Transaction>) : TransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            db.connector().apply {
                autoCommit = false
                transactionIsolation = isolation
            }
        }
        override val connection: Connection
            get() = connectionLazy.value

        override val outerTransaction: Transaction? = threadLocal.get()

        override fun commit() {
            if (connectionLazy.isInitialized())
                connection.commit()
        }

        override fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed) {
                connection.rollback()
            }
        }

        override fun close() {
            try {
                if (connectionLazy.isInitialized()) connection.close()
            } finally {
                threadLocal.set(outerTransaction)
            }
        }

    }
}

fun <T> transaction(db: Database? = null, statement: Transaction.() -> T): T = transaction(TransactionManager.manager.defaultIsolationLevel, TransactionManager.manager.defaultRepetitionAttempts, db, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: Transaction.() -> T): T {
    val outer = TransactionManager.currentOrNull()

    return if (outer != null && (db == null || outer.db == db)) {
        outer.statement()
    } else {
        val existingForDb = db?.let { TransactionManager.managerFor(it) }
        existingForDb?.currentOrNull()?.let {
            val currentManager = TransactionManager.manager
            try {
                TransactionManager.resetCurrent(existingForDb)
                it.statement()
            } finally {
                TransactionManager.resetCurrent(currentManager)
            }
        } ?: inTopLevelTransaction(transactionIsolation, repetitionAttempts, db, statement)
    }
}

private fun TransactionInterface.rollbackLoggingException(log: (Exception) -> Unit){
    try {
        rollback()
    } catch (e: Exception){
        log(e)
    }
}

private inline fun TransactionInterface.closeLoggingException(log: (Exception) -> Unit){
    try {
        close()
    } catch (e: Exception){
        log(e)
    }
}

fun <T> inTopLevelTransaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: Transaction.() -> T): T {
    var repetitions = 0

    val outerManager = TransactionManager.manager.takeIf { TransactionManager.currentOrNull() != null }
    while (true) {
        db?.let { TransactionManager.managerFor(db)?.let { m -> TransactionManager.resetCurrent(m) } }
        val transaction = TransactionManager.manager.newTransaction(transactionIsolation)

        try {
            val answer = transaction.statement()
            transaction.commit()
            return answer
        }
        catch (e: SQLException) {
            val exposedSQLException = e as? ExposedSQLException
            val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
            val message = "Transaction attempt #$repetitions failed: ${e.message}. Statement(s): $queriesToLog"
            exposedSQLException?.contexts?.forEach {
                transaction.interceptors.filterIsInstance<SqlLogger>().forEach { logger ->
                    logger.log(it, transaction)
                }
            }
            exposedLogger.warn(message, e)
            transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. See previous log line for statement", it) }
            repetitions++
            if (repetitions >= repetitionAttempts) {
                throw e
            }
        }
        catch (e: Throwable) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
            throw e
        }
        finally {
            TransactionManager.resetCurrent(outerManager)
            val currentStatement = transaction.currentStatement
            try {
                currentStatement?.let {
                    if(!it.isClosed) it.close()
                    transaction.currentStatement = null
                }
                transaction.closeExecutedStatements()
            } catch (e: Exception) {
                exposedLogger.warn("Statements close failed", e)
            }
            transaction.closeLoggingException { exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it) }
        }
    }
}