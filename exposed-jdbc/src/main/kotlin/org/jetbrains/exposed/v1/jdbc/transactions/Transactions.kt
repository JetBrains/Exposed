package org.jetbrains.exposed.v1.jdbc.transactions

import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @param db Database to use for the transaction. Defaults to `null`.
 * @param transactionIsolation Transaction isolation level. Defaults to `db.transactionManager.defaultIsolationLevel`.
 * @param readOnly Whether the transaction should be read-only. Defaults to `db.transactionManager.defaultReadOnly`.
 * @return The final result of the [statement] block.
 * @sample org.jetbrains.exposed.v1.tests.shared.ConnectionTimeoutTest.testTransactionRepetitionWithDefaults
 */
fun <T> transaction(
    db: Database? = null,
    transactionIsolation: Int = db.transactionManager.defaultIsolationLevel,
    readOnly: Boolean = db.transactionManager.defaultReadOnly,
    statement: JdbcTransaction.() -> T
): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, readOnly, outer)
        @Suppress("TooGenericExceptionCaught")
        try {
            transaction.statement().also {
                if (outer.db.useNestedTransactions) {
                    transaction.commit()
                }
            }
        } catch (cause: SQLException) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException {
                exposedLogger.warn(
                    "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                    it
                )
            }
            throw cause
        } catch (cause: Throwable) {
            if (outer.db.useNestedTransactions) {
                val currentStatement = transaction.currentStatement
                transaction.rollbackLoggingException {
                    exposedLogger.warn(
                        "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                        it
                    )
                }
            }
            throw cause
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
                    if (db.useNestedTransactions) {
                        transaction.commit()
                    }
                }
            } finally {
                TransactionManager.resetCurrent(currentManager)
            }
        } ?: inTopLevelTransaction(
            db,
            transactionIsolation,
            readOnly,
            null,
            statement
        )
    }
}

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** All changes in this transaction will be committed at the end of the [statement] block, even if
 * it is nested and even if `DatabaseConfig.useNestedTransactions` is set to `false`.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @param db Database to use for the transaction. Defaults to `null`.
 * @param transactionIsolation Transaction isolation level. Defaults to `db.transactionManager.defaultIsolationLevel`.
 * @param readOnly Whether the transaction should be read-only. Defaults to `db.transactionManager.defaultReadOnly`.
 * @param outerTransaction Outer transaction if this is a nested transaction. Defaults to `null`.
 * @return The final result of the [statement] block.
 * @sample org.jetbrains.exposed.v1.tests.shared.RollbackTransactionTest.testRollbackWithoutSavepoints
 */
fun <T> inTopLevelTransaction(
    db: Database? = null,
    transactionIsolation: Int = db.transactionManager.defaultIsolationLevel,
    readOnly: Boolean = db.transactionManager.defaultReadOnly,
    outerTransaction: JdbcTransaction? = null,
    statement: JdbcTransaction.() -> T
): T {
    fun run(): T {
        var attempts = 0

        val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

        var intermediateDelay: Long = 0
        var retryInterval: Long? = null

        while (true) {
            db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
            val transaction = db.transactionManager.newTransaction(transactionIsolation, readOnly, outerTransaction)

            @Suppress("TooGenericExceptionCaught")
            try {
                transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                val answer = transaction.statement()
                transaction.commit()
                return answer
            } catch (cause: SQLException) {
                handleSQLException(cause, transaction, attempts)
                attempts++
                if (attempts >= transaction.maxAttempts) {
                    throw cause
                }

                if (retryInterval == null) {
                    retryInterval = transaction.getRetryInterval()
                    intermediateDelay = transaction.minRetryDelay
                }
                // set delay value with an exponential backoff time period.
                val delay = when {
                    transaction.minRetryDelay < transaction.maxRetryDelay -> {
                        intermediateDelay += retryInterval * attempts
                        ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                    }

                    transaction.minRetryDelay == transaction.maxRetryDelay -> transaction.minRetryDelay
                    else -> 0
                }
                exposedLogger.warn("Wait $delay milliseconds before retrying")
                try {
                    Thread.sleep(delay)
                } catch (cause: InterruptedException) {
                    // Do nothing
                }
            } catch (cause: Throwable) {
                val currentStatement = transaction.currentStatement
                transaction.rollbackLoggingException {
                    exposedLogger.warn(
                        "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                        it
                    )
                }
                throw cause
            } finally {
                TransactionManager.resetCurrent(outerManager)
                closeStatementsAndConnection(transaction)
            }
        }
    }

    return keepAndRestoreTransactionRefAfterRun(db) {
        run()
    }
}

private fun <T> keepAndRestoreTransactionRefAfterRun(db: Database? = null, block: () -> T): T {
    val manager = db.transactionManager
    val currentTransaction = manager.currentOrNull()
    return try {
        block()
    } finally {
        manager.bindTransactionToThread(currentTransaction)
    }
}
