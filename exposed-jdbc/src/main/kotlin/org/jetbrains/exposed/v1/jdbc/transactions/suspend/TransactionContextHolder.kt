package org.jetbrains.exposed.v1.jdbc.transactions.suspend

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import kotlin.coroutines.CoroutineContext

/**
 * A context holder that associates a `JdbcTransaction` with a specific `CoroutineContext`.
 */
internal interface TransactionContextHolder : CoroutineContext.Element {
    val transaction: JdbcTransaction?
}
