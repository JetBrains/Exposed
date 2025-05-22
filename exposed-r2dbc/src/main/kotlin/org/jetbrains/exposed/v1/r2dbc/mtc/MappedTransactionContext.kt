package org.jetbrains.exposed.v1.r2dbc.mtc

import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction

internal object MappedTransactionContext {
    private const val MAPPED_TRANSACTION_CONTEXT_KEY = "r2dbc_exposed_mapped_transaction_context"

    fun setTransaction(transaction: R2dbcTransaction?) {
        MappedThreadContext.put(MAPPED_TRANSACTION_CONTEXT_KEY, transaction)
    }

    fun getTransactionOrNull(): R2dbcTransaction? = MappedThreadContext.get(MAPPED_TRANSACTION_CONTEXT_KEY) as? R2dbcTransaction

    fun clean() {
        MappedThreadContext.remove(MAPPED_TRANSACTION_CONTEXT_KEY)
    }
}
