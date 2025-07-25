package org.jetbrains.exposed.v1.jdbc.statements.api

import org.jetbrains.exposed.v1.core.statements.StatementResult
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult

/**
 * Base class representing a precompiled SQL [java.sql.PreparedStatement] from the JDBC SPI.
 */
interface JdbcPreparedStatementApi : PreparedStatementApi {
    /** The number of result set rows that should be fetched when generated by an executed statement. */
    var fetchSize: Int?

    /** The number of seconds the JDBC driver will wait for a statement to execute. */
    var timeout: Int?

    /** Adds parameters to the statement's batch of SQL commands. */
    fun addBatch()

    /**
     * Executes an SQL query stored in a [java.sql.PreparedStatement].
     *
     * @return The [JdbcResult] generated by the query.
     */
    fun executeQuery(): JdbcResult

    /**
     * Executes an SQL statement stored in a [java.sql.PreparedStatement].
     *
     * @return The affected row count if the executed statement is a DML type;
     * otherwise, 0 if the statement returns nothing.
     */
    fun executeUpdate(): Int

    /**
     * Executes multiple SQL statements stored in a single [java.sql.PreparedStatement].
     *
     * @return A list of [StatementResult]s retrieved from the database, which may store either affected row counts
     * or wrapped results. The order of elements is based on the order of the statements in the `PreparedStatement`.
     */
    fun executeMultiple(): List<StatementResult>

    /** The [JdbcResult] generated by the executed statement, or `null` if none was retrieved. */
    val resultSet: JdbcResult?

    /** Closes the statement, if still open, and releases any of its database and/or driver resources. */
    fun closeIfPossible()

    /**
     * Executes batched SQL statements stored as a [java.sql.PreparedStatement].
     *
     * @return A list of the affected row counts, with one element for each statement,
     * ordered based on the order in which statements were provided to the batch.
     */
    fun executeBatch(): List<Int>

    /** Cancels the statement, if supported by the database. */
    fun cancel()
}
