package org.jetbrains.exposed.r2dbc.sql.transactions

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabase
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcExposedConnection
import org.jetbrains.exposed.sql.transactions.TransactionInterface

interface R2dbcTransactionInterface : TransactionInterface {
    override val db: R2dbcDatabase

    override val outerTransaction: R2dbcTransaction?

    /** The transaction isolation level of the transaction, which may differ from the set database level. */
    val transactionIsolation: IsolationLevel

    /** The database connection used by the transaction. */
    val connection: R2dbcExposedConnection<*>

    /** Saves all changes since the last commit or rollback operation. */
    suspend fun commit()

    /** Reverts all changes since the last commit or rollback operation, or to the last set savepoint, if applicable. */
    suspend fun rollback()

    /** Closes the transaction and releases any savepoints. */
    suspend fun close()
}

/**
 * The [TransactionManager] instance that is associated with this [Database].
 *
 * @throws [RuntimeException] If a manager has not been registered for the database.
 */
@Suppress("TooGenericExceptionThrown")
val R2dbcDatabase?.transactionManager: TransactionManager
    get() = TransactionManager.managerFor(this)
        ?: throw RuntimeException("Database $this does not have any transaction manager")

@Suppress("TooGenericExceptionCaught")
internal suspend fun R2dbcTransactionInterface.rollbackLoggingException(log: (Exception) -> Unit) {
    try {
        rollback()
    } catch (e: Exception) {
        log(e)
    }
}

@Suppress("TooGenericExceptionCaught")
internal suspend inline fun R2dbcTransactionInterface.closeLoggingException(log: (Exception) -> Unit) {
    try {
        close()
    } catch (e: Exception) {
        log(e)
    }
}
