package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.coroutines.CoroutineContext

suspend fun <T> suspendedTransaction(context: CoroutineContext? = null, db: Database? = null, statement: suspend Transaction.() -> T): T {
    return suspendedTransactionAsync(context, db, statement = statement).await().result
}

suspend fun <T> Transaction.suspendedTransaction(context: CoroutineContext? = null, statement: suspend Transaction.() -> T): T {
    return suspendedTransactionAsyncInternal(context, db, currentTransaction = this, statement = statement).await().result
}

class TransactionResult<T>(internal val transaction: Transaction, val result:T)

suspend fun <T, R> Deferred<TransactionResult<T>>.andThen(statement: suspend Transaction.(T) -> R) : Deferred<TransactionResult<R>> {
    val firstResult = this.await()
    return suspendedTransactionAsync {
        with(firstResult.transaction) {
            suspendedMutex.withLock {
                statement(firstResult.result)
            }
        }
    }
}

suspend fun <T> suspendedTransactionAsync(context: CoroutineContext? = null, db: Database? = null,
                                          useOuterTransactionIfAccessible: Boolean = true, statement: suspend Transaction.() -> T) : Deferred<TransactionResult<T>> {
    val currentTransaction = TransactionManager.currentOrNull().takeIf { useOuterTransactionIfAccessible }
    return suspendedTransactionAsyncInternal(context, db, currentTransaction, statement)
}

suspend private fun <T> suspendedTransactionAsyncInternal(context: CoroutineContext? = null, db: Database? = null,
                                                          currentTransaction: Transaction?, statement: suspend Transaction.() -> T) : Deferred<TransactionResult<T>> = coroutineScope {
    val manager = db?.let { TransactionManager.managerFor(it) } ?: TransactionManager.manager
    val threadLocalManager = manager as? ThreadLocalTransactionManager

    if (currentTransaction != null) {
        val scope = (if (threadLocalManager != null) {
            (context ?: coroutineContext) + threadLocalManager.threadLocal.asContextElement(currentTransaction)
        } else (context ?: coroutineContext)) + TransactionManager.currentThreadManager.asContextElement(manager)
        async(scope) {
            currentTransaction.suspendedMutex.withLock {
                TransactionResult(currentTransaction, currentTransaction.statement())
            }
        }
    } else

        transaction(manager.defaultIsolationLevel, manager.defaultRepetitionAttempts, db) {
            val tx  = this
            val scope = (if (threadLocalManager != null) {
                (context ?: coroutineContext) + threadLocalManager.threadLocal.asContextElement(tx)
            } else (context ?: coroutineContext)) + TransactionManager.currentThreadManager.asContextElement()
            async(scope) {
                tx.suspendedMutex.withLock {
                    TransactionResult(tx, tx.statement())
                }
            }
    }
}