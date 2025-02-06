package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcPreparedStatementImpl

/** Represents a wrapper for a database connection. */
@Suppress("TooManyFunctions")
interface R2dbcExposedConnection<OriginalConnection : Any> {
    /** The underlying database connection object contained by this wrapper. */
    val connection: OriginalConnection

    /** Retrieve the name of the connection's catalog. */
    suspend fun getCatalog(): String

    /** Set the name of the connection's catalog. */
    suspend fun setCatalog(value: String)

    /** Retrieve the name of the connection's schema. */
    suspend fun getSchema(): String

    /** Set the name of the connection's schema. */
    suspend fun setSchema(value: String)

    /** Retrieve whether the connection is in auto-commit mode. */
    suspend fun getAutoCommit(): Boolean

    /** Set whether the connection is in auto-commit mode or not. */
    suspend fun setAutoCommit(value: Boolean)

    /** Set whether the connection is in read-only mode. */
    suspend fun setReadOnly(value: Boolean)

    /** Retrieves the transaction isolation level of the connection. */
    suspend fun getTransactionIsolation(): Int

    /** Sets the transaction isolation level of the connection. */
    suspend fun setTransactionIsolation(value: Int)

    /** Saves all changes since the last commit or rollback operation. */
    suspend fun commit()

    /** Reverts all changes since the last commit or rollback operation. */
    suspend fun rollback()

    /** Whether the connection has been closed. */
    suspend fun isClosed(): Boolean

    /** Closes the connection and releases any of its database and/or driver resources. */
    suspend fun close()

    /**
     * Returns a precompiled [sql] statement stored as a [PreparedStatementApi] implementation.
     *
     * To indicate that auto-generated keys should be made available for retrieval, set [returnKeys] to `true`.
     */
    suspend fun prepareStatement(sql: String, returnKeys: Boolean): R2dbcPreparedStatementImpl

    /**
     * Returns a precompiled [sql] statement stored as a [PreparedStatementApi] implementation.
     *
     * To indicate that auto-generated keys should be made available for retrieval, provide the names of
     * the target [columns] that contain the keys to be returned.
     */
    suspend fun prepareStatement(sql: String, columns: Array<String>): R2dbcPreparedStatementImpl

    /** Sends a collection of SQL strings to the database for execution as a batch statement. */
    suspend fun executeInBatch(sqls: List<String>)

    /**
     * Calls the specified function [body] with an [ExposedDatabaseMetadata] implementation as its receiver and
     * returns the retrieved metadata as a result.
     */
    suspend fun <T> metadata(body: suspend R2dbcExposedDatabaseMetadata.() -> T): T

    /** Sets and returns a new savepoint with the specified [name]. */
    suspend fun setSavepoint(name: String): ExposedSavepoint

    /** Removes the specified [savepoint]. */
    suspend fun releaseSavepoint(savepoint: ExposedSavepoint)

    /** Reverts all changes since the specified [savepoint] was set. */
    suspend fun rollback(savepoint: ExposedSavepoint)
}
