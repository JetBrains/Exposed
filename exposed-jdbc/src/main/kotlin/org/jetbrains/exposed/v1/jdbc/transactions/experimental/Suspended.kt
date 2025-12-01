package org.jetbrains.exposed.v1.jdbc.transactions.experimental

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.coroutines.CoroutineContext

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with `suspendTransaction()` call. `suspendTransaction()` doesn't allow to pass
        context as a parameter, use `withContext()` if you need to use transaction with custom context.
    """,
    level = DeprecationLevel.WARNING
)
suspend fun <T> newSuspendedTransaction(
    context: CoroutineContext? = null,
    db: Database? = null,
    transactionIsolation: Int? = null,
    readOnly: Boolean? = null,
    statement: suspend JdbcTransaction.() -> T
): T {
    return withContext(context ?: currentCoroutineContext()) {
        inTopLevelSuspendTransaction(db, transactionIsolation, readOnly, null, statement)
    }
}

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with `suspendTransaction()` call. `suspendTransaction()` doesn't allow to pass
        context as a parameter, use `withContext()` if you need to use transaction with custom context.
    """,
    level = DeprecationLevel.WARNING
)
suspend fun <T> JdbcTransaction.withSuspendTransaction(
    context: CoroutineContext? = null,
    statement: suspend JdbcTransaction.() -> T
): T {
    val tx = this
    return withContext(context ?: currentCoroutineContext()) {
        suspendTransaction(tx.db, tx.transactionIsolation, tx.readOnly, statement)
    }
}

@Deprecated(
    message = """"
        This function will be removed in future releases.

        Replace with `suspendTransaction()` call. `suspendTransaction()` doesn't allow to pass
        context as a parameter, use `withContext()` if you need to use transaction with custom context.

        To get result asynchronously, use `async` or `launch` from kotlinx.coroutines package.
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
    val scope = CoroutineScope(context ?: currentCoroutineContext())

    return scope.async {
        inTopLevelSuspendTransaction(db, transactionIsolation, readOnly, null, statement)
    }
}
