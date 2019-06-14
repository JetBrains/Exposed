package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

@Deprecated("Use suspendedTransaction", ReplaceWith("suspendedTransaction"), DeprecationLevel.WARNING)
suspend fun <T> transaction(db: Database? = null, statement: suspend Transaction.() -> T): T =
        transaction(TransactionManager.manager.defaultIsolationLevel, TransactionManager.manager.defaultRepetitionAttempts, db, statement)

@Deprecated("Use suspendedTransaction", ReplaceWith("suspendedTransaction"), DeprecationLevel.WARNING)
suspend fun <T> transaction(transactionIsolation: Int, repetitionAttempts: Int, db: Database? = null, statement: suspend Transaction.() -> T): T =
        org.jetbrains.exposed.sql.transactions.transaction(transactionIsolation, repetitionAttempts, db) {
            runBlocking { statement() }
        }


suspend fun <T> suspendedTransaction(context: CoroutineContext? = null, db: Database? = null, statement: Transaction.() -> T): T {
    val threadLocalManager = (db?.let { TransactionManager.managerFor(it) } ?: TransactionManager.manager) as? ThreadLocalTransactionManager
    val currentContext = context ?: coroutineContext
    val scope = threadLocalManager?.let { currentContext + it.threadLocal.asContextElement() } ?: currentContext
    return withContext(scope) {
        transaction(TransactionManager.manager.defaultIsolationLevel, TransactionManager.manager.defaultRepetitionAttempts, db, statement)
    }
}
