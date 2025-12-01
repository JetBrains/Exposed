package org.jetbrains.exposed.v1.r2dbc.statements

import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that either inserts a new row into a table,
 * or updates the existing row if insertion would violate a unique constraint.
 */
open class UpsertSuspendExecutable<Key : Any>(
    override val statement: UpsertStatement<Key>
) : InsertSuspendExecutable<Key, UpsertStatement<Key>>(statement) {
    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection().prepareStatement(sql, true)
        }

        return super.prepared(transaction, sql)
    }
}
