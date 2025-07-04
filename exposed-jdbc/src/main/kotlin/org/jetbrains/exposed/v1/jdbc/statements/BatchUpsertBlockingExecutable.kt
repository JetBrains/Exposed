package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.statements.BatchUpsertStatement
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that either batch inserts new rows into a table,
 * or updates the existing rows if insertions violate unique constraints.
 */
open class BatchUpsertBlockingExecutable(
    override val statement: BatchUpsertStatement
) : BatchInsertBlockingExecutable<BatchUpsertStatement>(statement) {
    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, statement.shouldReturnGeneratedValues)
        }

        return super.prepared(transaction, sql)
    }
}
