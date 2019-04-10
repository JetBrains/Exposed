package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import java.sql.Connection
import java.sql.SQLException
import java.sql.Savepoint

class ThreadLocalTransactionManager(private val db: Database,
                                    @Volatile override var defaultIsolationLevel: Int,
                                    @Volatile override var defaultRepetitionAttempts: Int) : TransactionManager {

    val threadLocal = ThreadLocal<Transaction>()

    override fun newTransaction(isolation: Int, outerTransaction: Transaction?): Transaction = Transaction(
        ThreadLocalTransaction(
            db = db,
            transactionIsolation = if (outerTransaction != null) outerTransaction.transactionIsolation else isolation,
            threadLocal = threadLocal,
            outerTransaction = outerTransaction,
            connectionProvider = {
                if (outerTransaction == null) {
                    db.connector().apply {
                        autoCommit = false
                        this.transactionIsolation = isolation
                    }
                } else {
                    outerTransaction.connection
                }
            }
        )
    ).apply {
        threadLocal.set(this)
    }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    private class ThreadLocalTransaction(
        override val db: Database,
        override val transactionIsolation: Int,
        val threadLocal: ThreadLocal<Transaction>,
        override val outerTransaction: Transaction?,
        private val connectionProvider: () -> Connection = { db.connector() }
    ) : TransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) { connectionProvider() }
        override val connection: Connection
            get() = connectionLazy.value

        private val topLevelTransaction = outerTransaction == null
        private var savepoint: Savepoint? = if (!topLevelTransaction) {
            connection.setSavepoint("POINT_$name")
        } else null


        override fun commit() {
            if (connectionLazy.isInitialized()) {
                if (topLevelTransaction) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed) {
                if (topLevelTransaction) {
                    connection.rollback()
                } else {
                    connection.rollback(savepoint)
                    savepoint = connection.setSavepoint("POINT_$name")
                }
            }
        }

        override fun close() {
            try {
                if (topLevelTransaction) {
                    if (connectionLazy.isInitialized()) connection.close()
                } else {
                    savepoint.let {
                        connection.releaseSavepoint(it)
                        savepoint = null
                    }
                }
            } finally {
                threadLocal.set(outerTransaction)
            }
        }

        private val name: String
            get() {
                var p: Transaction? = outerTransaction
                var value: String = ""

                while (p != null) {
                    value = p.hashCode().toString(16) + "_" + value
                    p = p.outerTransaction
                }
                return value + this.hashCode().toString(16)
            }
    }
}

fun <T> transaction(db: Database? = null, statement: Transaction.() -> T): T =
    transaction(db.transactionManager.defaultIsolationLevel, db.transactionManager.defaultRepetitionAttempts, db, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: Transaction.() -> T): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
        val transaction = db.transactionManager.newTransaction(transactionIsolation, outer)
        try {
            transaction.statement().also {
                transaction.commit()
            }
        } finally {
            TransactionManager.resetCurrent(outerManager)
        }
    } else {
        val existingForDb = db?.let { db.transactionManager }
        existingForDb?.currentOrNull()?.let { transaction ->
            val currentManager = outer?.db.transactionManager
            try {
                TransactionManager.resetCurrent(existingForDb)
                transaction.statement().also {
                    transaction.commit()
                }
            } finally {
                TransactionManager.resetCurrent(currentManager)
            }
        } ?: inTopLevelTransaction(transactionIsolation, repetitionAttempts, db, null, statement)
    }
}

private fun TransactionInterface.rollbackLoggingException(log: (Exception) -> Unit) {
    try {
        rollback()
    } catch (e: Exception) {
        log(e)
    }
}

private inline fun TransactionInterface.closeLoggingException(log: (Exception) -> Unit) {
    try {
        close()
    } catch (e: Exception) {
        log(e)
    }
}

fun <T> inTopLevelTransaction(
    transactionIsolation: Int,
    repetitionAttempts: Int,
    db: Database? = null,
    outerTransaction: Transaction? = null,
    statement: Transaction.() -> T
): T {

    fun run():T {
        var repetitions = 0

        val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

        while (true) {
            db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
            val transaction = db.transactionManager.newTransaction(transactionIsolation, outerTransaction)

            try {
                val answer = transaction.statement()
                transaction.commit()
                return answer
            } catch (e: SQLException) {
                val exposedSQLException = e as? ExposedSQLException
                val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n")
                    ?: "${transaction.currentStatement}"
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
            } catch (e: Throwable) {
                val currentStatement = transaction.currentStatement
                transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
                throw e
            } finally {
                TransactionManager.resetCurrent(outerManager)
                val currentStatement = transaction.currentStatement
                try {
                    currentStatement?.let {
                        if (!it.isClosed) it.close()
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

    return keepAndRestoreTransactionRefAfterRun(db) {
        run()
    }
}

internal fun <T> keepAndRestoreTransactionRefAfterRun(db: Database? = null, block: () -> T): T {
    val manager = db.transactionManager as? ThreadLocalTransactionManager
    val currentTransaction = manager?.threadLocal?.get()
    return block().also {
        manager?.threadLocal?.set(currentTransaction)
    }
}
