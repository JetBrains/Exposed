package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.Transaction

/** Base representation for a unit block of work that is performed on a database. */
interface TransactionInterface {
    /** The database on which the transaction tasks are performed. */
    val db: DatabaseApi

    /** The transaction manager that manages this transaction instance. */
    val transactionManager: TransactionManagerApi

    /** Whether the transaction is in read-only mode. */
    val readOnly: Boolean

    /** The parent transaction of a nested transaction; otherwise, `null` if the transaction is a top-level instance. */
    val outerTransaction: Transaction?
}
