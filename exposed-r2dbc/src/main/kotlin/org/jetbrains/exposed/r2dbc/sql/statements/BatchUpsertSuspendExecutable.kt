package org.jetbrains.exposed.r2dbc.sql.statements

import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.BatchUpsertStatement
import org.jetbrains.exposed.sql.vendors.currentDialect

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
