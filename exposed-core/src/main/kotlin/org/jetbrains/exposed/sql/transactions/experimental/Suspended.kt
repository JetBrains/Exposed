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
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

suspend fun <T> Transaction.suspendedTransaction(context: CoroutineContext? = null, statement: suspend Transaction.() -> T): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null) {
        suspendedTransactionAsyncInternal(false, statement).await()
    }

suspend fun <T> suspendedTransactionAsync(
    context: CoroutineContext? = null,
    db: Database? = null,
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
    statement: suspend Transaction.() -> T
): Deferred<T> = async {
    val transaction = tx.value
    @Suppress("TooGenericExceptionCaught")
    try {
        transaction.statement().apply {
            if (shouldCommit) transaction.commit()
        }
    } catch (e: SQLException) {
        handleSQLException(e, transaction, 1)
        throw e
    } catch (e: Throwable) {
        val currentStatement = transaction.currentStatement
        transaction.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
        throw e
    } finally {
        if (shouldCommit) transaction.closeAsync()
    }
}
