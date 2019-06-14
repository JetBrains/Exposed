package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

suspend fun <T> suspendedTransaction(context: CoroutineContext? = null, db: Database? = null, statement: Transaction.() -> T): T {
    val threadLocalManager = (db?.let { TransactionManager.managerFor(it) } ?: TransactionManager.manager) as? ThreadLocalTransactionManager
    val currentContext = context ?: coroutineContext
    val scope = threadLocalManager?.let { currentContext + it.threadLocal.asContextElement() } ?: currentContext
    return withContext(scope) {
        transaction(TransactionManager.manager.defaultIsolationLevel, TransactionManager.manager.defaultRepetitionAttempts, db, statement)
    }
}
