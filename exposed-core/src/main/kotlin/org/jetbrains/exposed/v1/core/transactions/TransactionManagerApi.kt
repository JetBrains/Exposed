package org.jetbrains.exposed.v1.core.transactions

/**
 * Represents the manager registered to a database, which is responsible for creating new transactions
 * and storing data related to the database and its transactions.
 */
interface TransactionManagerApi {
    /** Whether transactions should be performed in read-only mode. Unless specified, the database default will be used. */
    var defaultReadOnly: Boolean

    /** The default maximum amount of attempts that will be made to perform a transaction. */
    var defaultMaxAttempts: Int

    /** The default minimum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMinRetryDelay: Long

    /** The default maximum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMaxRetryDelay: Long
}
