package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import io.r2dbc.spi.TransactionDefinition

internal class R2dbcTransactionDefinition(
    val isolationLevel: IsolationLevel?,
    val readOnly: Boolean,
    val statementTimeout: Int?,
) : TransactionDefinition {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getAttribute(option: Option<T?>): T? {
        return getKnownAttribute(option) as? T
    }

    private fun getKnownAttribute(option: Option<*>): Any? = when (option) {
        TransactionDefinition.ISOLATION_LEVEL -> isolationLevel
        TransactionDefinition.READ_ONLY -> readOnly
        else -> null
    }
}
