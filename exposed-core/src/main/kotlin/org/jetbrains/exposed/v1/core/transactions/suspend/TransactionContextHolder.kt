package org.jetbrains.exposed.v1.core.transactions.suspend

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import kotlin.coroutines.CoroutineContext

/**
 * A context holder that associates a `Transaction` with a specific `CoroutineContext`.
 */
interface TransactionContextHolder : CoroutineContext.Element {
    val transaction: Transaction?
}

/**
 * A context holder that associates a `Transaction` with a specific `CoroutineContext`.
 * @suppress
 */
@InternalApi
data class TransactionContextHolderImpl(
    override val transaction: Transaction?,
    override val key: CoroutineContext.Key<*>
) : TransactionContextHolder
