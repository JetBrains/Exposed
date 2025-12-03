package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import io.r2dbc.spi.TransactionDefinition

internal class R2dbcTransactionDefinition(
    val isolationLevel: IsolationLevel?,
    val readOnly: Boolean?,
) : TransactionDefinition {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getAttribute(option: Option<T?>): T? {
        return getKnownAttribute(option) as? T
    }

    /**
     * There are other available attributes, like NAME & LOCK_TIMEOUT, but Exposed currently has no need for these,
     * so they are set to `null`. This means the driver will defer to its own (or the database's own) defaults
     * for these attributes.
     */
    private fun getKnownAttribute(option: Option<*>): Any? = when (option) {
        TransactionDefinition.ISOLATION_LEVEL -> isolationLevel
        TransactionDefinition.READ_ONLY -> readOnly
        else -> null
    }

    /**
     * Oracle driver does not allow [TransactionDefinition] with both isolation level and read-only set together,
     * but Exposed needs to always pass both, even if they are set to defaults. This ensures that read-only
     * will be set using manual SQL string that matches driver SQL, while isolation level will be set directly by
     * driver that uses more complex logic.
     */
    fun toOracleDefinition(): R2dbcTransactionDefinition = R2dbcTransactionDefinition(
        this.isolationLevel,
        null,
    )
}
