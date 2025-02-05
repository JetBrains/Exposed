package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.DatabaseApi
import org.jetbrains.exposed.sql.Transaction

/** Represents a unit block of work that is performed on a database. */
interface TransactionInterface {
    /** The database on which the transaction tasks are performed. */
    val db: DatabaseApi

    /** Whether the transaction is in read-only mode. */
    val readOnly: Boolean

    /** The parent transaction of a nested transaction; otherwise, `null` if the transaction is a top-level instance. */
    val outerTransaction: Transaction?
}
