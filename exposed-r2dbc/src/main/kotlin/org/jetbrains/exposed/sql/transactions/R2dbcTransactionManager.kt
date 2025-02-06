package org.jetbrains.exposed.sql.transactions

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.R2dbcDatabase
import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedConnection
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * [TransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class R2dbcTransactionManager(
    private val db: R2dbcDatabase,
    private val setupTxConnection: ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)? = null
) : TransactionManager {
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    override var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
        get() = if (field == -1) R2dbcDatabase.getDefaultIsolationLevel(db) else field

    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    // coroutine equivalent as context element?
    val threadLocal = ThreadLocal<R2dbcTransaction>()

    override fun toString(): String {
        return "R2dbcTransactionManager[${hashCode()}](db=$db)"
    }

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): R2dbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions } as? R2dbcTransaction
            ?: R2dbcTransaction(
                R2dbcThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    threadLocal = threadLocal,
                    outerTransaction = outerTransaction as? R2dbcTransaction,
                ),
            )

        return transaction.apply { bindTransactionToThread(this) }
    }

    override fun currentOrNull(): R2dbcTransaction? = threadLocal.get()

    override fun bindTransactionToThread(transaction: Transaction?) {
        if (transaction != null) {
            threadLocal.set(transaction as R2dbcTransaction)
        } else {
            threadLocal.remove()
        }
    }

    private class R2dbcThreadLocalTransaction(
        override val db: R2dbcDatabase,
        private val setupTxConnection: ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        val threadLocal: ThreadLocal<R2dbcTransaction>,
        override val outerTransaction: R2dbcTransaction?,
    ) : R2dbcTransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@R2dbcThreadLocalTransaction)
                } catch (e: Exception) {
                    try {
                        // suspend alternative for lazy?
//                        close()
                    } catch (closeException: Exception) {
                        e.addSuppressed(closeException)
                    }
                    throw e
                }
            }
        }

        override val connection: R2dbcExposedConnection<*>
            get() = connectionLazy.value

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions

        // how to use a suspend function result to set a property value
//        private var savepoint: ExposedSavepoint? = if (useSavePoints) connection.setSavepoint(savepointName) else null
        private var savepoint: ExposedSavepoint? = null

        override suspend fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override suspend fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed()) {
                if (useSavePoints && savepoint != null) {
                    connection.rollback(savepoint!!)
                    savepoint = connection.setSavepoint(savepointName)
                } else {
                    connection.rollback()
                }
            }
        }

        override suspend fun close() {
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
                while (currenTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currenTransaction = currenTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}

// naming

/**
 * Creates a new `TransactionScope` then calls the specified suspending [statement], suspends until it completes,
 * and returns the result.
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 */
suspend fun <T> suspendTransaction(
    context: CoroutineContext? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: Int? = null,
    statement: suspend R2dbcTransaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

/**
 * Calls the specified suspending [statement], suspends until it completes, and returns the result.
 *
 * The resulting `TransactionScope` is derived from the current [CoroutineContext] if the latter already holds
 * [this] `Transaction`; otherwise, a new scope is created using [this] `Transaction` and a given coroutine [context].
 */
suspend fun <T> R2dbcTransaction.suspendTransaction(
    context: CoroutineContext? = null,
    statement: suspend R2dbcTransaction.() -> T
): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null) {
        suspendedTransactionAsyncInternal(false, statement).await()
    }

/**
 * Creates a new `TransactionScope` and returns its future result as an implementation of [Deferred].
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 */
suspend fun <T> suspendTransactionAsync(
    context: CoroutineContext? = null,
    db: R2dbcDatabase? = null,
    transactionIsolation: Int? = null,
    statement: suspend R2dbcTransaction.() -> T
): Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull() as? R2dbcTransaction
    return withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(!holdsSameTransaction(currentTransaction), statement)
    }
}

private suspend fun R2dbcTransaction.closeAsync() {
    val currentTransaction = TransactionManager.currentOrNull() as? R2dbcTransaction
    try {
        val temporaryManager = this.db.transactionManager
        temporaryManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(temporaryManager)
    } finally {
        closeStatementsAndConnection(this)
        val transactionManager = currentTransaction?.db?.transactionManager
        transactionManager?.bindTransactionToThread(currentTransaction)
        TransactionManager.resetCurrent(transactionManager)
    }
}

private suspend fun <T> withTransactionScope(
    context: CoroutineContext?,
    currentTransaction: R2dbcTransaction?,
    db: R2dbcDatabase? = null,
    transactionIsolation: Int?,
    body: suspend TransactionScope.() -> T
): T {
    val currentScope = coroutineContext[TransactionScope]

    @OptIn(InternalApi::class)
    suspend fun newScope(currentTransaction: R2dbcTransaction?): T {
        val currentDatabase: R2dbcDatabase? = (
            currentTransaction?.db
                ?: db
                ?: TransactionManager.currentDefaultDatabase.get()
            ) as? R2dbcDatabase
        val manager = (
            currentDatabase?.transactionManager
                ?: TransactionManager.manager
            ) as R2dbcTransactionManager

        val tx = lazy(LazyThreadSafetyMode.NONE) {
            currentTransaction ?: manager.newTransaction(transactionIsolation ?: manager.defaultIsolationLevel)
        }

        val element = TransactionCoroutineElement(tx, manager)

        val newContext = context ?: coroutineContext

        return TransactionScope(tx, newContext + element).body()
    }

    val sameTransaction = currentScope?.holdsSameTransaction(currentTransaction) == true
    val sameContext = context == coroutineContext
    return when {
        currentScope == null -> newScope(currentTransaction)
        sameTransaction && sameContext -> currentScope.body()
        else -> newScope(currentTransaction)
    }
}

private suspend fun R2dbcTransaction.resetIfClosed(): R2dbcTransaction {
    return if (connection.isClosed()) {
        // Repetition attempts will throw org.h2.jdbc.JdbcSQLException: The object is already closed
        // unless the transaction is reset before every attempt (after the 1st failed attempt)
        val currentManager = db.transactionManager as R2dbcTransactionManager
        currentManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(currentManager)
        currentManager.newTransaction(transactionIsolation, readOnly, outerTransaction)
    } else {
        this
    }
}

@Suppress("CyclomaticComplexMethod")
private suspend fun <T> TransactionScope.suspendedTransactionAsyncInternal(
    shouldCommit: Boolean,
    statement: suspend R2dbcTransaction.() -> T
): Deferred<T> = async {
    var attempts = 0
    var intermediateDelay: Long = 0
    var retryInterval: Long? = null

    var answer: T
    while (true) {
        val transaction = if (attempts == 0) tx.value else tx.value.resetIfClosed()

        @Suppress("TooGenericExceptionCaught")
        try {
            answer = transaction.statement().apply {
                if (shouldCommit) transaction.commit()
            }
            break
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
            // set delay value with an exponential backoff time period
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
        } catch (cause: Throwable) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException {
                exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it)
            }
            throw cause
        } finally {
            if (shouldCommit) transaction.closeAsync()
        }
    }
    answer
}

internal suspend fun handleSQLException(cause: SQLException, transaction: R2dbcTransaction, attempts: Int) {
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
