package org.jetbrains.exposed.v1.r2dbc.transactions

import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import kotlin.coroutines.CoroutineContext

/**
 * A context holder that associates a `R2dbcTransaction` with a specific `CoroutineContext`.
 */
internal interface TransactionContextHolder : CoroutineContext.Element {
    val transaction: R2dbcTransaction?
}
