package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.withTransactionContext
import java.util.concurrent.ThreadLocalRandom

/**
 * Executes the provided [block] within the context of the [transaction], handling commit and rollback operations.
 *
 * This internal helper function extracts common error handling logic for R2DBC transaction execution.
 * It catches both [R2dbcException] and general [Throwable] exceptions to ensure proper transaction
 * rollback and resource cleanup.
 *
 * @param transaction The transaction in which to execute the block
 * @param shouldCommit Whether the transaction should be committed after successful execution
 * @param block The suspend code block to execute within the transaction context
 * @return The result of executing the block
 * @throws R2dbcException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution (after attempting rollback)
 */
@Suppress("TooGenericExceptionCaught")
private suspend inline fun <T> executeR2dbcTransactionWithErrorHandling(
    transaction: R2dbcTransaction,
    shouldCommit: Boolean,
    crossinline block: suspend () -> T
): T {
    return try {
        block().also {
            if (shouldCommit) {
                transaction.commit()
            }
        }
    } catch (cause: R2dbcException) {
        val currentStatement = transaction.currentStatement
        transaction.rollbackLoggingException {
            exposedLogger.warn(
                "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                it
            )
        }
        throw cause
    } catch (cause: Throwable) {
        if (shouldCommit) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException {
                exposedLogger.warn(
                    "Transaction rollback failed: ${it.message}. Statement: $currentStatement",
                    it
                )
            }
        }
        throw cause
    }
}

/**
 * Resolves the R2DBC database to use for a transaction.
 *
 * Resolution order:
 * 1. The explicitly provided [db] parameter
 * 2. The database from the current transaction (if within an existing transaction)
 * 3. The current default database from [TransactionManager.primaryDatabase]
 *
 * @param db Optional database explicitly specified by the caller
 * @return The resolved database instance
 * @throws IllegalStateException If no database can be resolved
 */
@OptIn(InternalApi::class)
private fun resolveR2dbcDatabaseOrThrow(db: R2dbcDatabase?): R2dbcDatabase {
    return db
        ?: ThreadLocalTransactionsStack.getTransactionIsInstance(R2dbcTransaction::class.java)?.db
        ?: TransactionManager.primaryDatabase
        ?: throw IllegalStateException(
            "No R2DBC database specified and no default database found. " +
                "Please call R2dbcDatabase.connect() first or specify a database explicitly in the transaction call."
        )
}

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [R2dbcDatabase] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * **Note** This function catches all throwables (including errors) to ensure proper transaction rollback
 * and resource cleanup, even in exceptional circumstances.
 *
 * @param db Database to use for the transaction. Defaults to `null`.
 * @param transactionIsolation Transaction isolation level. Defaults to `db.transactionManager.defaultIsolationLevel`.
 * @param readOnly Whether the transaction should be read-only. Defaults to `db.transactionManager.defaultReadOnly`.
 * @return The final result of the [statement] block.
 * @throws IllegalStateException If no database is available
 * @throws R2dbcException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 */
suspend fun <T> suspendTransaction(
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    statement: suspend R2dbcTransaction.() -> T
): T {
    val databaseToUse = resolveR2dbcDatabaseOrThrow(db)
    val outer = databaseToUse.transactionManager.getCurrentContextTransaction()

    return if (outer != null) {
        val transaction = outer.transactionManager.newTransaction(
            transactionIsolation ?: outer.transactionManager.defaultIsolationLevel,
            readOnly ?: outer.transactionManager.defaultReadOnly,
            outer
        )

        @OptIn(InternalApi::class)
        withTransactionContext(transaction) {
            executeR2dbcTransactionWithErrorHandling(
                transaction,
                shouldCommit = outer.db.useNestedTransactions
            ) {
                transaction.statement()
            }
        }
    } else {
        inTopLevelSuspendTransaction(
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
 * it is nested and even if `R2dbcDatabaseConfig.useNestedTransactions` is set to `false`.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [R2dbcDatabase] instance created
 * or the value associated with the parent transaction (if this function is invoked in an existing transaction).
 *
 * **Note** This function catches all throwables (including errors) to ensure proper transaction rollback
 * and resource cleanup, even in exceptional circumstances.
 *
 * @param db Database to use for the transaction. Defaults to `null`.
 * @param transactionIsolation Transaction isolation level. Defaults to `db.transactionManager.defaultIsolationLevel`.
 * @param readOnly Whether the transaction should be read-only. Defaults to `db.transactionManager.defaultReadOnly`.
 * @param outerTransaction Outer transaction if this is a nested transaction. Defaults to `null`.
 * @return The final result of the [statement] block.
 * @throws IllegalStateException If no database is available
 * @throws R2dbcException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 */
suspend fun <T> inTopLevelSuspendTransaction(
    db: R2dbcDatabase? = null,
    transactionIsolation: IsolationLevel? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    outerTransaction: R2dbcTransaction? = null,
    statement: suspend R2dbcTransaction.() -> T
): T {
    var attempts = 0
    var intermediateDelay: Long = 0
    var retryInterval: Long? = null

    val database = resolveR2dbcDatabaseOrThrow(db)

    while (true) {
        val transaction = database.transactionManager.newTransaction(
            transactionIsolation ?: database.transactionManager.defaultIsolationLevel,
            readOnly ?: database.transactionManager.defaultReadOnly,
            outerTransaction
        )

        try {
            @OptIn(InternalApi::class)
            return withTransactionContext(transaction) {
                try {
                    executeR2dbcTransactionWithErrorHandling(transaction, shouldCommit = true) {
                        transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                        transaction.statement()
                    }
                } catch (cause: R2dbcException) {
                    handleR2dbcException(cause, transaction, attempts)
                    throw cause
                }
            }
        } catch (cause: R2dbcException) {
            attempts++

            if (retryInterval == null) {
                retryInterval = transaction.getRetryInterval()
                intermediateDelay = transaction.minRetryDelay
            }

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

            if (attempts >= transaction.maxAttempts) {
                throw cause
            }
        } finally {
            @OptIn(InternalApi::class)
            withTransactionContext(transaction) {
                closeStatementsAndConnection(transaction)
            }
        }
    }
}

/**
 * Handles R2DBC exceptions that occur during transaction execution.
 *
 * This function logs the exception details, including the queries that caused the exception,
 * and attempts to roll back the transaction.
 *
 * @param cause The R2dbcException that occurred.
 * @param transaction The transaction in which the exception occurred.
 * @param attempts The number of transaction attempts made so far.
 */
internal suspend fun handleR2dbcException(cause: R2dbcException, transaction: R2dbcTransaction, attempts: Int) {
    val exposedR2dbcException = cause as? ExposedR2dbcException
    val queriesToLog = exposedR2dbcException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
    val message = "Transaction attempt #$attempts failed: ${cause.message}. Statement(s): $queriesToLog"
    exposedR2dbcException?.contexts?.forEach {
        transaction.interceptors.filterIsInstance<SqlLogger>().forEach { logger ->
            logger.log(it, transaction)
        }
    }
    exposedLogger.warn(message, cause)
    transaction.rollbackLoggingException {
        exposedLogger.warn("Transaction rollback failed: ${it.message}. See previous log line for statement", it)
    }
}

/**
 * Closes all statements and the connection associated with a transaction.
 *
 * This function ensures proper cleanup of resources by closing the current statement,
 * all executed statements, and the transaction connection. Any exceptions during cleanup are logged.
 *
 * @param transaction The transaction whose resources should be closed.
 */
internal suspend fun closeStatementsAndConnection(transaction: R2dbcTransaction) {
    val currentStatement = transaction.currentStatement
    @Suppress("TooGenericExceptionCaught")
    try {
        currentStatement?.let {
            it.closeIfPossible()
            transaction.currentStatement = null
        }
        transaction.closeExecutedStatements()
    } catch (cause: Exception) {
        exposedLogger.warn("Statements close failed", cause)
    }
    transaction.closeLoggingException {
        exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it)
    }
}
