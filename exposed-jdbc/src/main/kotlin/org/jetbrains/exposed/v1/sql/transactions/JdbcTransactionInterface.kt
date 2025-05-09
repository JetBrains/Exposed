package org.jetbrains.exposed.v1.sql.transactions

import org.jetbrains.exposed.v1.core.transactions.TransactionInterface
import org.jetbrains.exposed.v1.sql.Database
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.ExposedConnection

// TODO add missed KDocs
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
// TODO check if we can move it to Database to make it field/method there.
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
