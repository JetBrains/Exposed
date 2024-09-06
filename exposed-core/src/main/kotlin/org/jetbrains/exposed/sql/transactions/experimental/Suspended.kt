package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.closeStatementsAndConnection
import org.jetbrains.exposed.sql.transactions.handleSQLException
import org.jetbrains.exposed.sql.transactions.rollbackLoggingException
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class TransactionContext(val manager: TransactionManager?, val transaction: Transaction?)

internal class TransactionScope(
    internal val tx: Lazy<Transaction>,
    parent: CoroutineContext
) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    internal fun holdsSameTransaction(transaction: Transaction?) =
        transaction != null && tx.isInitialized() && tx.value == transaction
    companion object : CoroutineContext.Key<TransactionScope>
}

internal class TransactionCoroutineElement(
    private val newTransaction: Lazy<Transaction>,
    val manager: TransactionManager
) : ThreadContextElement<TransactionContext> {
    override val key: CoroutineContext.Key<TransactionCoroutineElement> = Companion

    override fun updateThreadContext(context: CoroutineContext): TransactionContext {
        val currentTransaction = TransactionManager.currentOrNull()
        val currentManager = currentTransaction?.db?.transactionManager
        manager.bindTransactionToThread(newTransaction.value)
        TransactionManager.resetCurrent(manager)
        return TransactionContext(currentManager, currentTransaction)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionContext) {
        manager.bindTransactionToThread(oldState.transaction)
        TransactionManager.resetCurrent(oldState.manager)
    }

    companion object : CoroutineContext.Key<TransactionCoroutineElement>
}

/**
 * Creates a new `TransactionScope` then calls the specified suspending [statement], suspends until it completes,
 * and returns the result.
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.CoroutineTests.suspendedTx
 */
suspend fun <T> newSuspendedTransaction(
    context: CoroutineContext? = null,
    db: DatabaseApi? = null,
    transactionIsolation: Int? = null,
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

/**
 * Calls the specified suspending [statement], suspends until it completes, and returns the result.
 *
 * The resulting `TransactionScope` is derived from the current [CoroutineContext] if the latter already holds
 * [this] `Transaction`; otherwise, a new scope is created using [this] `Transaction` and a given coroutine [context].
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.CoroutineTests.suspendedTx
 */
suspend fun <T> Transaction.withSuspendTransaction(
    context: CoroutineContext? = null,
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null) {
        suspendedTransactionAsyncInternal(false, statement).await()
    }

/**
 * Creates a new `TransactionScope` and returns its future result as an implementation of [Deferred].
 *
 * The `TransactionScope` is derived from a new `Transaction` and a given coroutine [context],
 * or the current [CoroutineContext] if no [context] is provided.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.CoroutineTests.suspendTxAsync
 */
suspend fun <T> suspendedTransactionAsync(
    context: CoroutineContext? = null,
    db: DatabaseApi? = null,
    transactionIsolation: Int? = null,
    statement: suspend Transaction.() -> T
): Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(!holdsSameTransaction(currentTransaction), statement)
    }
}

private fun Transaction.closeAsync() {
    val currentTransaction = TransactionManager.currentOrNull()
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
    currentTransaction: Transaction?,
    db: DatabaseApi? = null,
    transactionIsolation: Int?,
    body: suspend TransactionScope.() -> T
): T {
    val currentScope = coroutineContext[TransactionScope]
    suspend fun newScope(currentTransaction: Transaction?): T {
        val currentDatabase: DatabaseApi? = currentTransaction?.db ?: db ?: TransactionManager.currentDefaultDatabase.get()
        val manager = currentDatabase?.transactionManager ?: TransactionManager.manager

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

private fun Transaction.resetIfClosed(): Transaction {
    return if (connection.isClosed) {
        // Repetition attempts will throw org.h2.jdbc.JdbcSQLException: The object is already closed
        // unless the transaction is reset before every attempt (after the 1st failed attempt)
        val currentManager = db.transactionManager
        currentManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(currentManager)
        currentManager.newTransaction(transactionIsolation, readOnly, outerTransaction)
    } else {
        this
    }
}

@Suppress("CyclomaticComplexMethod")
private fun <T> TransactionScope.suspendedTransactionAsyncInternal(
    shouldCommit: Boolean,
    statement: suspend Transaction.() -> T
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
