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
): T {
    return withContext(context ?: currentCoroutineContext()) {
        inTopLevelSuspendTransaction(db, transactionIsolation, readOnly, null, statement)
    }
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
): T {
    val tx = this
    return withContext(context ?: currentCoroutineContext()) {
        suspendTransaction(tx.db, tx.transactionIsolation, tx.readOnly, statement)
    }
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
    val scope = CoroutineScope(context ?: currentCoroutineContext())

    return scope.async {
        suspendTransaction(db, transactionIsolation, readOnly, statement)
    }
}
