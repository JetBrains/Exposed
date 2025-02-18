package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect

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
