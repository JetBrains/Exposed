package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class TransactionContext(val manager: TransactionManager?, val transaction: Transaction?)

internal class TransactionScope(internal val tx: Lazy<Transaction>, parent: CoroutineContext) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    companion object : CoroutineContext.Key<TransactionScope>
}

internal class TransactionCoroutineElement(val newTransaction: Lazy<Transaction>, val manager: TransactionManager) : ThreadContextElement<TransactionContext> {
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
    context: CoroutineDispatcher? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    statement: suspend Transaction.() -> T
): T =
    withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(true, statement).await()
    }

suspend fun <T> Transaction.suspendedTransaction(context: CoroutineDispatcher? = null, statement: suspend Transaction.() -> T): T =
    withTransactionScope(context, this, db = null, transactionIsolation = null) {
        suspendedTransactionAsyncInternal(false, statement).await()
    }

suspend fun <T> suspendedTransactionAsync(
    context: CoroutineDispatcher? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    statement: suspend Transaction.() -> T
) : Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db, transactionIsolation) {
        suspendedTransactionAsyncInternal(currentTransaction != tx, statement)
    }
}

private fun Transaction.commitInAsync() {
    val currentTransaction = TransactionManager.currentOrNull()
    try {
        val temporaryManager = this.db.transactionManager
        temporaryManager.bindTransactionToThread(this)
        TransactionManager.resetCurrent(temporaryManager)
        try {
            commit()
            try {
                currentStatement?.let {
                    it.closeIfPossible()
                    currentStatement = null
                }
                closeExecutedStatements()
            } catch (e: Exception) {
                exposedLogger.warn("Statements close failed", e)
            }
            closeLoggingException { exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it) }
        } catch (e: Exception) {
            rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
            throw e
        }
    } finally {
        val transactionManager = currentTransaction?.db?.transactionManager
        transactionManager?.bindTransactionToThread(currentTransaction)
        TransactionManager.resetCurrent(transactionManager)
    }
}

private suspend fun <T> withTransactionScope(context: CoroutineContext?,
                                             currentTransaction: Transaction?,
                                             db: Database? = null,
                                             transactionIsolation: Int?,
                                             body: suspend TransactionScope.() -> T) : T {
    val currentScope = coroutineContext[TransactionScope]
    suspend fun newScope(_tx: Transaction?) : T {
        val manager = (_tx?.db ?: db)?.transactionManager ?: TransactionManager.manager

        val tx = lazy(LazyThreadSafetyMode.NONE){ _tx ?: manager.newTransaction(transactionIsolation ?: manager.defaultIsolationLevel) }

        val element = TransactionCoroutineElement(tx, manager)

        val newContext = context ?: coroutineContext

       return TransactionScope(tx, newContext + element).body()
    }
    val sameTransaction = currentTransaction == currentScope?.tx
    val sameContext = context == coroutineContext
    return when {
        currentScope == null -> newScope(currentTransaction)
        sameTransaction && sameContext -> currentScope.body()
        else -> newScope(currentTransaction)
    }
}

private fun <T> TransactionScope.suspendedTransactionAsyncInternal(shouldCommit: Boolean,
                                                          statement: suspend Transaction.() -> T) : Deferred<T>
    = async {
            try {
                tx.value.statement()
            } catch (e: Throwable) {
                tx.value.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: ${tx.value.currentStatement}", it) }
                throw e
            } finally {
                if (shouldCommit) tx.value.commitInAsync()
            }
        }