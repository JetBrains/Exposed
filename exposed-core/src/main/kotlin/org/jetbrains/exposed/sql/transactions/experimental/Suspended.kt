package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.async
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
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

internal class TransactionScope(internal val tx: Lazy<Transaction>, parent: CoroutineContext) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    internal fun holdsSameTransaction(transaction: Transaction?) = transaction != null && tx.isInitialized() && tx.value == transaction
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

suspend fun <T> newSuspendedTransaction(
    context: CoroutineContext? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    repetitionAttempts: Int = 0,
    minRepetitionDelay: Long = 0,
    maxRepetitionDelay: Long = 0,
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(true, repetitionAttempts, minRepetitionDelay, maxRepetitionDelay, statement).await()
    }

suspend fun <T> Transaction.suspendedTransaction(
    context: CoroutineContext? = null,
    repetitionAttempts: Int = 0,
    minRepetitionDelay: Long = 0,
    maxRepetitionDelay: Long = 0,
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null) {
        suspendedTransactionAsyncInternal(false, repetitionAttempts, minRepetitionDelay, maxRepetitionDelay, statement).await()
    }

suspend fun <T> suspendedTransactionAsync(
    context: CoroutineContext? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    repetitionAttempts: Int = 0,
    minRepetitionDelay: Long = 0,
    maxRepetitionDelay: Long = 0,
    statement: suspend Transaction.() -> T
): Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(
            !holdsSameTransaction(currentTransaction), repetitionAttempts, minRepetitionDelay, maxRepetitionDelay, statement
        )
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
    db: Database? = null,
    transactionIsolation: Int?,
    body: suspend TransactionScope.() -> T
): T {
    val currentScope = coroutineContext[TransactionScope]
    suspend fun newScope(_tx: Transaction?): T {
        val manager = (_tx?.db ?: db ?: TransactionManager.currentDefaultDatabase.get())?.transactionManager ?: TransactionManager.manager

        val tx = lazy(LazyThreadSafetyMode.NONE) { _tx ?: manager.newTransaction(transactionIsolation ?: manager.defaultIsolationLevel) }

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

private fun <T> TransactionScope.suspendedTransactionAsyncInternal(
    shouldCommit: Boolean,
    repetitionAttempts: Int,
    minRepetitionDelay: Long,
    maxRepetitionDelay: Long,
    statement: suspend Transaction.() -> T
): Deferred<T> = async {
    var repetitions = 0
    var intermediateDelay = minRepetitionDelay
    val retryInterval = if (repetitionAttempts > 0) {
        maxOf((maxRepetitionDelay - minRepetitionDelay) / (repetitionAttempts + 1), 1)
    } else 0

    var answer: T
    while (true) {
        val transaction = tx.value.run {
            if (connection.isClosed) {
                // Repetition attempts will throw org.h2.jdbc.JdbcSQLException: The object is already closed
                // unless the transaction is reset before every attempt (after the 1st failed attempt)
                val currentManager = db.transactionManager.also { TransactionManager.resetCurrent(it) }
                currentManager.newTransaction(transactionIsolation, readOnly, outerTransaction)
            } else {
                this
            }
        }
        @Suppress("TooGenericExceptionCaught")
        try {
            answer = transaction.statement().apply {
                if (shouldCommit) transaction.commit()
            }
            break
        } catch (e: SQLException) {
            handleSQLException(e, transaction, repetitions)
            repetitions++
            if (repetitions >= repetitionAttempts) {
                throw e
            }
            // set delay value with an exponential backoff time period.
            val delay = when {
                minRepetitionDelay < maxRepetitionDelay -> {
                    intermediateDelay += retryInterval * repetitions
                    ThreadLocalRandom.current().nextLong(intermediateDelay, intermediateDelay + retryInterval)
                }
                minRepetitionDelay == maxRepetitionDelay -> minRepetitionDelay
                else -> 0
            }
            exposedLogger.warn("Wait $delay milliseconds before retrying")
            try {
                Thread.sleep(delay)
            } catch (e: InterruptedException) {
                // Do nothing
            }
        } catch (e: Throwable) {
            val currentStatement = transaction.currentStatement
            transaction.rollbackLoggingException {
                exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it)
            }
            throw e
        } finally {
            if (shouldCommit) transaction.closeAsync()
        }
    }
    answer
}
