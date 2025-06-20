package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that either inserts a new row into a table,
 * or updates the existing row if insertion would violate a unique constraint.
 */
open class UpsertBlockingExecutable<Key : Any>(
    override val statement: UpsertStatement<Key>
) : InsertBlockingExecutable<Key, UpsertStatement<Key>>(statement) {
    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, true)
        }

        return super.prepared(transaction, sql)
    }
}
