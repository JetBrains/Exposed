package org.jetbrains.exposed.v1.jdbc.transactions

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.withThreadLocalTransaction
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.withTransactionContext
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom

/**
 * Executes the provided [block] within the context of the [transaction], handling commit and rollback operations.
 *
 * This internal helper function extracts common error handling logic for transaction execution.
 * It catches both [SQLException] and general [Throwable] exceptions to ensure proper transaction
 * rollback and resource cleanup.
 *
 * @param transaction The transaction in which to execute the block
 * @param shouldCommit Whether the transaction should be committed after successful execution
 * @param block The code block to execute within the transaction context
 * @return The result of executing the block
 * @throws SQLException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution (after attempting rollback)
 */
@Suppress("TooGenericExceptionCaught")
private inline fun <T> executeTransactionWithErrorHandling(
    transaction: JdbcTransaction,
    shouldCommit: Boolean,
    block: () -> T
): T {
    return try {
        block().also {
            if (shouldCommit) {
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
 * Resolves the database to use for a transaction.
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
private fun resolveDatabaseOrThrow(db: Database?): Database {
    return db
        ?: ThreadLocalTransactionsStack.getTransactionIsInstance(JdbcTransaction::class.java)?.db
        ?: TransactionManager.primaryDatabase
        ?: throw IllegalStateException(
            "No database specified and no default database found. " +
                "Please call Database.connect() first or specify a database explicitly in the transaction call."
        )
}

/**
 * Creates a transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
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
 * @throws SQLException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 * @sample org.jetbrains.exposed.v1.tests.shared.ConnectionTimeoutTest.testTransactionRepetitionWithDefaults
 */
fun <T> transaction(
    db: Database? = null,
    transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    statement: JdbcTransaction.() -> T
): T {
    val database = resolveDatabaseOrThrow(db)

    @OptIn(InternalApi::class)
    val outer = database.transactionManager.currentOrNull()

    return if (outer != null) {
        val transaction = outer.transactionManager.newTransaction(
            transactionIsolation ?: outer.transactionManager.defaultIsolationLevel,
            readOnly ?: outer.transactionManager.defaultReadOnly,
            outer
        )

        @OptIn(InternalApi::class)
        withThreadLocalTransaction(transaction) {
            executeTransactionWithErrorHandling(
                transaction,
                shouldCommit = outer.db.useNestedTransactions
            ) {
                transaction.statement()
            }
        }
    } else {
        inTopLevelTransaction(
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
 * **Note** This function catches all throwables (including errors) to ensure proper transaction rollback
 * and resource cleanup, even in exceptional circumstances.
 *
 * @param db Database to use for the transaction. Defaults to `null`.
 * @param transactionIsolation Transaction isolation level. Defaults to `db.transactionManager.defaultIsolationLevel`.
 * @param readOnly Whether the transaction should be read-only. Defaults to `db.transactionManager.defaultReadOnly`.
 * @param outerTransaction Outer transaction if this is a nested transaction. Defaults to `null`.
 * @return The final result of the [statement] block.
 * @throws IllegalStateException If no database is available
 * @throws SQLException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 * @sample org.jetbrains.exposed.v1.tests.shared.RollbackTransactionTest.testRollbackWithoutSavepoints
 */
fun <T> inTopLevelTransaction(
    db: Database? = null,
    transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    outerTransaction: JdbcTransaction? = null,
    statement: JdbcTransaction.() -> T
): T {
    var attempts = 0
    var intermediateDelay: Long = 0
    var retryInterval: Long? = null

    val database = resolveDatabaseOrThrow(db)

    while (true) {
        val transaction = database.transactionManager.newTransaction(
            transactionIsolation ?: database.transactionManager.defaultIsolationLevel,
            readOnly ?: database.transactionManager.defaultReadOnly,
            outerTransaction
        )

        try {
            @OptIn(InternalApi::class)
            return withThreadLocalTransaction(transaction) {
                try {
                    executeTransactionWithErrorHandling(transaction, shouldCommit = true) {
                        transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                        transaction.statement()
                    }
                } catch (cause: SQLException) {
                    handleSQLException(cause, transaction, attempts)
                    throw cause
                }
            }
        } catch (cause: SQLException) {
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
                Thread.sleep(retryDelay)
            } catch (cause: InterruptedException) {
                // Do nothing
            }

            if (attempts >= transaction.maxAttempts) {
                throw cause
            }
        } finally {
            @OptIn(InternalApi::class)
            withThreadLocalTransaction(transaction) {
                closeStatementsAndConnection(transaction)
            }
        }
    }
}

/**
 * Creates a suspendable transaction with the specified [transactionIsolation] and [readOnly] settings, then calls
 * the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
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
 * @throws SQLException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 */
suspend fun <T> suspendTransaction(
    db: Database? = null,
    transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    statement: suspend JdbcTransaction.() -> T
): T {
    val databaseToUse = resolveDatabaseOrThrow(db)

    val outer = databaseToUse.transactionManager.getCurrentContextTransaction()

    return if (outer != null) {
        val transaction = outer.transactionManager.newTransaction(
            transactionIsolation ?: outer.transactionManager.defaultIsolationLevel,
            readOnly ?: outer.transactionManager.defaultReadOnly,
            outer
        )

        @OptIn(InternalApi::class)
        withTransactionContext(transaction) {
            executeTransactionWithErrorHandling(
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
 * Creates a suspendable transaction at the top level with the specified [transactionIsolation] and [readOnly] settings,
 * then calls the [statement] block with this transaction as its receiver and returns the result.
 *
 * **Note** All changes in this transaction will be committed at the end of the [statement] block, even if
 * it is nested and even if `DatabaseConfig.useNestedTransactions` is set to `false`.
 *
 * **Note** If the database value [db] is not set, the value used will be either the last [Database] instance created
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
 * @throws SQLException If a database error occurs and retry attempts are exhausted
 * @throws Throwable If any other error occurs during execution
 */
suspend fun <T> inTopLevelSuspendTransaction(
    db: Database? = null,
    transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
    readOnly: Boolean? = db?.transactionManager?.defaultReadOnly,
    outerTransaction: JdbcTransaction? = null,
    statement: suspend JdbcTransaction.() -> T
): T {
    var attempts = 0
    var intermediateDelay: Long = 0
    var retryInterval: Long? = null

    val database = resolveDatabaseOrThrow(db)

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
                    executeTransactionWithErrorHandling(transaction, shouldCommit = true) {
                        transaction.db.config.defaultSchema?.let { SchemaUtils.setSchema(it) }
                        transaction.statement()
                    }
                } catch (cause: SQLException) {
                    handleSQLException(cause, transaction, attempts)
                    throw cause
                }
            }
        } catch (cause: SQLException) {
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
                Thread.sleep(retryDelay)
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
 * Handles SQL exceptions that occur during transaction execution.
 *
 * This function logs the exception details, including the queries that caused the exception,
 * and attempts to roll back the transaction.
 *
 * @param cause The SQLException that occurred.
 * @param transaction The transaction in which the exception occurred.
 * @param attempts The number of transaction attempts made so far.
 */
internal fun handleSQLException(cause: SQLException, transaction: JdbcTransaction, attempts: Int) {
    val exposedSQLException = cause as? ExposedSQLException
    val queriesToLog = exposedSQLException?.causedByQueries()?.joinToString(";\n") ?: "${transaction.currentStatement}"
    val message = "Transaction attempt #$attempts failed: ${cause.message}. Statement(s): $queriesToLog"
    exposedSQLException?.contexts?.forEach {
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
internal fun closeStatementsAndConnection(transaction: JdbcTransaction) {
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
