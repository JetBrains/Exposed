package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.reactivestreams.Publisher

/**
 * We wrap calls to `Publisher.awaitFirst()`, `Publisher.awaitFirstOrNull()`,
 * `Publisher.awaitLast()`, and `Publisher.awaitSingle()` with `Dispatchers.IO`
 * to avoid issues caused by incorrect context switching.
 *
 * Without this wrapper, it may happen that at the moment `Publisher.await*()` is invoked,
 * the transaction is removed from the stack by
 * [org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextElement]
 * and not restored after the operation completes.
 *
 * This behavior was observed in the issue:
 * [EXPOSED-877 Error 'No transaction in context' for select](https://youtrack.jetbrains.com/issue/EXPOSED-877)
 */
internal suspend fun <T> withPublisherAwaiting(block: suspend () -> T): T {
    return withContext(Dispatchers.IO) {
        block()
    }
}

internal suspend fun <T> awaitFirstOrNull(publisher: Publisher<T>): T? =
    withPublisherAwaiting { publisher.awaitFirstOrNull() }

internal suspend fun <T> awaitSingle(publisher: Publisher<T>): T =
    withPublisherAwaiting { publisher.awaitSingle() }

internal suspend fun <T> awaitLast(publisher: Publisher<T>): T =
    withPublisherAwaiting { publisher.awaitLast() }
