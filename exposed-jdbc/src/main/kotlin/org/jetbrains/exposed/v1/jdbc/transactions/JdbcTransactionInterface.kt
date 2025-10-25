package org.jetbrains.exposed.v1.jdbc.transactions

import org.jetbrains.exposed.v1.core.transactions.TransactionInterface
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection

/** Represents a unit block of work that is performed on a database using a JDBC driver. */
interface JdbcTransactionInterface : TransactionInterface {
    override val db: Database

    override val outerTransaction: JdbcTransaction?

    /** The transaction isolation level of the transaction, which may differ from the set database level. */
    val transactionIsolation: Int

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
val Database.transactionManager: TransactionManager
    get() = TransactionManager.managerFor(this)

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
