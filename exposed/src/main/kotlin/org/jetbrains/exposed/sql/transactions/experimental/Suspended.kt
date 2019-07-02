package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.rollbackLoggingException
import java.lang.Exception
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class TransactionCoroutineElement(val outerTransaction: Transaction?, val newTransaction: Transaction, val manager: TransactionManager) : ThreadContextElement<Transaction> /*by original*/ {
    override val key: CoroutineContext.Key<TransactionCoroutineElement> = Companion

    private var prevManager : TransactionManager? = null

    override fun updateThreadContext(context: CoroutineContext): Transaction {
        prevManager = TransactionManager.currentThreadManager.get()
        (manager as? ThreadLocalTransactionManager)?.let {
            it.threadLocal.set(newTransaction)
            TransactionManager.currentThreadManager.set(manager)
        }
        return newTransaction
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Transaction) {
        require(newTransaction == oldState)
        if (outerTransaction == null) {
            with(newTransaction) {
                try {
                    commit()
                    try {
                        currentStatement?.let {
                            it.close()
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
            }
            (manager as? ThreadLocalTransactionManager)?.threadLocal?.remove()

        } else {
            (manager as? ThreadLocalTransactionManager)?.threadLocal?.set(outerTransaction)
        }
        prevManager?.let {
            TransactionManager.currentThreadManager.set(it)
        } ?: TransactionManager.currentThreadManager.remove()
    }

    companion object : CoroutineContext.Key<TransactionCoroutineElement> {
        fun forTLManager(manager: TransactionManager, currentTransaction: Transaction?, newTransaction: Transaction) : ThreadContextElement<Transaction> {
            return TransactionCoroutineElement(currentTransaction, newTransaction, manager)
        }
    }
}

suspend fun <T> newSuspendedTransaction(context: CoroutineDispatcher? = null, db: Database? = null, statement: suspend Transaction.() -> T): T {
    return suspendedTransactionAsyncInternal(context, db, TransactionManager.currentOrNull(), false, statement).await()
}

suspend fun <T> Transaction.suspendedTransaction(context: CoroutineDispatcher? = null, statement: suspend Transaction.() -> T): T {
    val tx = this
    return suspendedTransactionAsyncInternal(context, db, tx, false, statement = statement).await()
}

class TransactionResult<T>(internal val transaction: Transaction,
                           internal val deferred: Deferred<T>) : Deferred<T> by deferred

suspend fun <T, R> TransactionResult<T>.andThen(statement: suspend Transaction.(T) -> R) : TransactionResult<R> {
    val currentAsync = this
    return suspendedTransactionAsyncInternal(currentTransaction = currentAsync.transaction, lazy = false) {
        statement(currentAsync.deferred.await())
    }
}

suspend fun <T> suspendedTransactionAsync(context: CoroutineDispatcher? = null, db: Database? = null,
                                          useOuterTransactionIfAccessible: Boolean = true, statement: suspend Transaction.() -> T) : TransactionResult<T> {
    val currentTransaction = TransactionManager.currentOrNull().takeIf { useOuterTransactionIfAccessible }
    return suspendedTransactionAsyncInternal(context, db, currentTransaction, false, statement)
}

private suspend fun <T> suspendedTransactionAsyncInternal(context: CoroutineDispatcher? = null, db: Database? = null,
                                                          currentTransaction: Transaction?, lazy: Boolean,
                                                          statement: suspend Transaction.() -> T) : TransactionResult<T> {
    val manager = (currentTransaction?.db ?: db)?.let { TransactionManager.managerFor(it) }
            ?: TransactionManager.manager

    val tx = currentTransaction ?: manager.newTransaction(manager.defaultIsolationLevel)
    val element = TransactionCoroutineElement.forTLManager(manager, currentTransaction, tx)
    val contextNew = (context ?: coroutineContext) + element
    return withContext(contextNew) {
        TransactionResult(tx, async {
            try {
                tx.statement()
            } catch (e: Throwable) {
                tx.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: ${tx.currentStatement}", it) }
                throw e
            }
        })
    }
}