package org.jetbrains.exposed.v1.r2dbc.statements

import org.jetbrains.exposed.v1.core.statements.BatchUpsertStatement
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that either batch inserts new rows into a table,
 * or updates the existing rows if insertions violate unique constraints.
 */
open class BatchUpsertSuspendExecutable(
    override val statement: BatchUpsertStatement
) : BatchInsertSuspendExecutable<BatchUpsertStatement>(statement) {
    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, statement.shouldReturnGeneratedValues)
        }

        return super.prepared(transaction, sql)
    }
}
