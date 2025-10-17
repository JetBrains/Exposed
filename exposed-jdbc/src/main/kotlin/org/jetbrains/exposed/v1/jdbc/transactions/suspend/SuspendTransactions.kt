package org.jetbrains.exposed.v1.jdbc.transactions.suspend

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.closeStatementsAndConnection
import org.jetbrains.exposed.v1.jdbc.transactions.handleSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.rollbackLoggingException
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 * Creates a transaction then calls the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 */
suspend fun <T> suspendTransaction(db: Database? = null, statement: suspend JdbcTransaction.() -> T): T =
    suspendTransaction(
        db.transactionManager.defaultIsolationLevel,
        db.transactionManager.defaultReadOnly,
        db,
        statement
    )

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * @return The final result of the [statement] block.
 */
suspend fun <T> suspendTransaction(
    transactionIsolation: Int,
    readOnly: Boolean = false,
    db: Database? = null,
    statement: suspend JdbcTransaction.() -> T
): T = keepAndRestoreTransactionRefAfterRun(db) {
    val outer = TransactionManager.currentOrNull()

    if (outer != null && (db == null || outer.db == db)) {
        val outerManager = outer.db.transactionManager

        val transaction = outerManager.newTransaction(transactionIsolation, readOnly, outer)
        withTransactionContext(transaction) {
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
        }
    } else {
        val currentContextTransaction = db?.transactionManager?.getCurrentContextTransaction()

        if (currentContextTransaction != null) {
            val currentManager = outer?.db.transactionManager
            val transaction = currentContextTransaction
            withTransactionContext(transaction) {
                try {
                    TransactionManager.resetCurrent(db.transactionManager)
                    transaction.statement().also {
                        if (db.useNestedTransactions) {
                            transaction.commit()
                        }
                    }
                } finally {
                    TransactionManager.resetCurrent(currentManager)
                }
            }
        } else {
            inTopLevelSuspendTransaction(
                transactionIsolation,
                readOnly,
                db,
                null,
                statement
            )
        }
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
 * @return The final result of the [statement] block.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> inTopLevelSuspendTransaction(
    transactionIsolation: Int,
    readOnly: Boolean = false,
    db: Database? = null,
    outerTransaction: JdbcTransaction? = null,
    statement: suspend JdbcTransaction.() -> T
): T {
    suspend fun run(): T {
        var attempts = 0

        val outerManager = outerTransaction?.db.transactionManager.takeIf { it.currentOrNull() != null }

        var intermediateDelay: Long = 0
        var retryInterval: Long? = null

        while (true) {
            db?.let { db.transactionManager.let { m -> TransactionManager.resetCurrent(m) } }
            val transaction = db.transactionManager.newTransaction(transactionIsolation, readOnly, outerTransaction)

            try {
                return withTransactionContext(transaction) {
                    try {
                        transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                        val answer = transaction.statement()
                        transaction.commit()
                        answer
                    } catch (cause: SQLException) {
                        handleSQLException(cause, transaction, attempts)
                        throw cause
                    } catch (cause: Throwable) {
                        val currentStatement = transaction.currentStatement
                        transaction.rollbackLoggingException {
                            exposedLogger.warn(
                                "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                                it
                            )
                        }
                        throw cause
                    }
                }
            } catch (cause: SQLException) {
                attempts++
                if (attempts >= transaction.maxAttempts) {
                    throw cause
                }

                if (retryInterval == null) {
                    retryInterval = transaction.getRetryInterval()
                    intermediateDelay = transaction.minRetryDelay
                }
                // set delay value with an exponential backoff time period.
                val retryDelay = when {
                    transaction.minRetryDelay < transaction.maxRetryDelay -> {
                        intermediateDelay += retryInterval * attempts
                        ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                    }

                    transaction.minRetryDelay == transaction.maxRetryDelay -> transaction.minRetryDelay
                    else -> 0
                }
                exposedLogger.warn("Wait $retryDelay milliseconds before retrying")
                try {
                    delay(retryDelay)
                } catch (cause: InterruptedException) {
                    // Do nothing
                }
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

private suspend fun <T> keepAndRestoreTransactionRefAfterRun(db: Database? = null, block: suspend () -> T): T {
    val manager = db.transactionManager
    val currentTransaction = manager.currentOrNull()
    return try {
        block()
    } finally {
        manager.bindTransactionToThread(currentTransaction)
    }
}

/**
 * The method creates context with provided transaction and runs code block within that context.
 *
 * @param transaction The transaction to be used in the context.
 * @param body The code block to be executed in the context.
 * @return The result of executing the code block.
 */
private suspend fun <T> withTransactionContext(transaction: JdbcTransaction, body: suspend () -> T): T {
    val manager = transaction.db.transactionManager
    val context = manager.createTransactionContext(transaction)

    return withContext(context) {
        body()
    }
}
