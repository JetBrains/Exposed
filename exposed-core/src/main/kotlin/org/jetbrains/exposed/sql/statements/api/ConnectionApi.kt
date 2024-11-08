@file:Suppress("Filename", "MatchingDeclarationName")

package org.jetbrains.exposed.sql.statements.api

/** Represents a wrapper for a database connection. */
interface ExposedConnection<OriginalConnection : Any> {
    /** Whether the connection has been closed. */
    val isClosed: Boolean

    /** Saves all changes since the last commit or rollback operation. */
    fun commit()

    /** Reverts all changes since the last commit or rollback operation. */
    fun rollback()

    /** Closes the connection and releases any of its database and/or driver resources. */
    fun close()

    /** Whether the connection is in auto-commit mode. */
    var autoCommit: Boolean

    /** Whether the connection is in read-only mode. */
    var readOnly: Boolean

    /** The transaction isolation level of the connection. */
    var transactionIsolation: Int

    /** The underlying database connection object contained by this wrapper. */
    val connection: OriginalConnection

    /**
     * Returns a precompiled [sql] statement stored as a [PreparedStatementApi] implementation.
     *
     * To indicate that auto-generated keys should be made available for retrieval, set [returnKeys] to `true`.
     */
    fun prepareStatement(sql: String, returnKeys: Boolean): PreparedStatementApi

    /**
     * Returns a precompiled [sql] statement stored as a [PreparedStatementApi] implementation.
     *
     * To indicate that auto-generated keys should be made available for retrieval, provide the names of
     * the target [columns] that contain the keys to be returned.
     */
    fun prepareStatement(sql: String, columns: Array<String>): PreparedStatementApi

    /** Sends a collection of SQL strings to the database for execution as a batch statement. */
    // potential BREAKING CHANGE
    suspend fun executeInBatch(sqls: List<String>)

    /** The name of the connection's catalog. */
    var catalog: String

    /** The name of the connection's schema. */
    var schema: String

    /**
     * Calls the specified function [body] with an [ExposedDatabaseMetadata] implementation as its receiver and
     * returns the retrieved metadata as a result.
     */
    fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T

    /** Sets and returns a new savepoint with the specified [name]. */
    fun setSavepoint(name: String): ExposedSavepoint

    /** Removes the specified [savepoint]. */
    fun releaseSavepoint(savepoint: ExposedSavepoint)

    /** Reverts all changes since the specified [savepoint] was set. */
    fun rollback(savepoint: ExposedSavepoint)
}
