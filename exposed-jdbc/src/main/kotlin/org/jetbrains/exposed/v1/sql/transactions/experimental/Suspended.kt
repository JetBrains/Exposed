package org.jetbrains.exposed.v1.sql.transactions.experimental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.sql.Database
import org.jetbrains.exposed.v1.sql.InternalApi
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.exposedLogger
import org.jetbrains.exposed.v1.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.sql.transactions.closeStatementsAndConnection
import org.jetbrains.exposed.v1.sql.transactions.handleSQLException
import org.jetbrains.exposed.v1.sql.transactions.rollbackLoggingException
import org.jetbrains.exposed.v1.sql.transactions.transactionManager
import java.sql.SQLException
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// TODO should we deprecate it and remove it?

internal class TransactionContext(val manager: TransactionManager?, val transaction: JdbcTransaction?)

internal class TransactionScope(
    internal val tx: Lazy<JdbcTransaction>,
    parent: CoroutineContext
) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    internal fun holdsSameTransaction(transaction: JdbcTransaction?) =
        transaction != null && tx.isInitialized() && tx.value == transaction

    companion object : CoroutineContext.Key<TransactionScope>
}

internal class TransactionCoroutineElement(
    private val newTransaction: Lazy<JdbcTransaction>,
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

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with `suspendTransaction()` from exposed-r2dbc instead to use a suspending transaction.

        Please leave a comment on [YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-74/Add-R2DBC-Support)
        with a use case if you believe this method should remain available for JDBC connections.
    """,
    level = DeprecationLevel.WARNING
)
suspend fun <T> newSuspendedTransaction(
    context: CoroutineContext? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    readOnly: Boolean? = null,
    statement: suspend JdbcTransaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation, readOnly) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with nested `suspendTransaction()` from exposed-r2dbc instead to use a suspending transaction.

        Please leave a comment on [YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-74/Add-R2DBC-Support)
        with a use case if you believe this method should remain available for JDBC connections.
    """,
    level = DeprecationLevel.WARNING
)
suspend fun <T> JdbcTransaction.withSuspendTransaction(
    context: CoroutineContext? = null,
    statement: suspend JdbcTransaction.() -> T
): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null, readOnly = null) {
        suspendedTransactionAsyncInternal(false, statement).await()
    }

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with `suspendTransactionAsync()` from exposed-r2dbc instead to use a suspending transaction.

        Please leave a comment on [YouTrack](https://youtrack.jetbrains.com/issue/EXPOSED-74/Add-R2DBC-Support)
        with a use case if you believe this method should remain available for JDBC connections.
    """,
    level = DeprecationLevel.WARNING
)
suspend fun <T> suspendedTransactionAsync(
    context: CoroutineContext? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    readOnly: Boolean? = null,
    statement: suspend JdbcTransaction.() -> T
): Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db, transactionIsolation, readOnly) {
        suspendedTransactionAsyncInternal(!holdsSameTransaction(currentTransaction), statement)
    }
}

private fun JdbcTransaction.closeAsync() {
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
    currentTransaction: JdbcTransaction?,
    db: Database? = null,
    transactionIsolation: Int?,
    readOnly: Boolean?,
    body: suspend TransactionScope.() -> T
): T {
    val currentScope = coroutineContext[TransactionScope]

    @OptIn(InternalApi::class)
    suspend fun newScope(currentTransaction: JdbcTransaction?): T {
        val currentDatabase: Database? = currentTransaction?.db
            ?: db
            ?: CoreTransactionManager.getDefaultDatabase() as? Database
        val manager = currentDatabase?.transactionManager ?: TransactionManager.manager

        val tx = lazy(LazyThreadSafetyMode.NONE) {
            currentTransaction ?: manager.newTransaction(
                isolation = transactionIsolation ?: manager.defaultIsolationLevel,
                readOnly = readOnly ?: manager.defaultReadOnly
            )
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

private fun JdbcTransaction.resetIfClosed(): JdbcTransaction {
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
    statement: suspend JdbcTransaction.() -> T
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
