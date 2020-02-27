package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import java.sql.SQLException

class ThreadLocalTransactionManager(private val db: Database,
                                    @Volatile override var defaultIsolationLevel: Int,
                                    @Volatile override var defaultRepetitionAttempts: Int) : TransactionManager {

    val threadLocal = ThreadLocal<Transaction>()

    override fun newTransaction(isolation: Int, outerTransaction: Transaction?): Transaction =
        (outerTransaction?.takeIf { !db.useNestedTransactions } ?: Transaction(
            ThreadLocalTransaction(
                db = db,
                transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                threadLocal = threadLocal,
                outerTransaction = outerTransaction
            )
        )).apply {
            threadLocal.set(this)
        }

    override fun currentOrNull(): Transaction? = threadLocal.get()

    private class ThreadLocalTransaction(
        override val db: Database,
        override val transactionIsolation: Int,
        val threadLocal: ThreadLocal<Transaction>,
        override val outerTransaction: Transaction?
    ) : TransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                autoCommit = false
                if (this@ThreadLocalTransaction.transactionIsolation != IGNORE_ISOLATION_LEVEL) {
                    transactionIsolation = this@ThreadLocalTransaction.transactionIsolation
                }
            }
        }
        override val connection: ExposedConnection<*>
            get() = connectionLazy.value

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions
        private var savepoint: ExposedSavepoint? = if (useSavePoints) {
            connection.setSavepoint(savepointName)
        } else null


        override fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed) {
                if (useSavePoints && savepoint != null) {
                    connection.rollback(savepoint!!)
                    savepoint = connection.setSavepoint(savepointName)
                } else {
                    connection.rollback()
                }
            }
        }

        override fun close() {
            try {
                if (!useSavePoints) {
                    if (connectionLazy.isInitialized()) connection.close()
                } else {
                    savepoint?.let {
                        connection.releaseSavepoint(it)
                        savepoint = null
                    }
                }
            } finally {
                threadLocal.set(outerTransaction)
            }
        }

        private val savepointName: String
            get() {
                var nestedLevel = 0
                var currenTransaction = outerTransaction
                while(currenTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currenTransaction = currenTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}

fun <T> transaction(db: Database? = null, statement: Transaction.() -> T): T =
    transaction(db.transactionManager.defaultIsolationLevel, db.transactionManager.defaultRepetitionAttempts, db, statement)

fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: Transaction.() -> T): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, outer)
        try {
            transaction.statement().also {
                if(outer.db.useNestedTransactions)
                    transaction.commit()
            }
        } finally {
            TransactionManager.resetCurrent(outerManager)
        }
    } else {
        val existingForDb = db?.transactionManager
        existingForDb?.currentOrNull()?.let { transaction ->
            val currentManager = outer?.db.transactionManager
            try {
                TransactionManager.resetCurrent(existingForDb)
                transaction.statement().also {
                    if(db.useNestedTransactions)
                        transaction.commit()
                }
            } finally {
                TransactionManager.resetCurrent(currentManager)
            }
        } ?: inTopLevelTransaction(transactionIsolation, repetitionAttempts, db, null, statement)
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
                        it.closeIfPossible()
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
    val currentTransaction = manager?.currentOrNull()
    return try {
        block()
    } finally {
        manager?.threadLocal?.set(currentTransaction)
    }
}
