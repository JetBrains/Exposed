package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.ExposedConnection

interface JdbcTransactionInterface : TransactionInterface {
    override val db: Database

    override val outerTransaction: JdbcTransaction?

    /** The database connection used by the transaction. */
    val connection: ExposedConnection<*>

    /** Saves all changes since the last commit or rollback operation. */
    fun commit()

    /** Reverts all changes since the last commit or rollback operation, or to the last set savepoint, if applicable. */
    fun rollback()

    /** Closes the transaction and releases any savepoints. */
    fun close()
}

/**
 * The [TransactionManager] instance that is associated with this [Database].
 *
 * @throws [RuntimeException] If a manager has not been registered for the database.
 */
@Suppress("TooGenericExceptionThrown")
val Database?.transactionManager: TransactionManager
    get() = TransactionManager.managerFor(this)
        ?: throw RuntimeException("Database $this does not have any transaction manager")

@Suppress("TooGenericExceptionCaught")
internal fun JdbcTransactionInterface.rollbackLoggingException(log: (Exception) -> Unit) {
    try {
        rollback()
    } catch (e: Exception) {
        log(e)
    }
}

@Suppress("TooGenericExceptionCaught")
internal inline fun JdbcTransactionInterface.closeLoggingException(log: (Exception) -> Unit) {
    try {
        close()
    } catch (e: Exception) {
        log(e)
    }
}
