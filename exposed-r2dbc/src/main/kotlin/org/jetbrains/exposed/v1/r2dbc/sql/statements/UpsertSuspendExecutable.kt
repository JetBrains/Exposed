package org.jetbrains.exposed.v1.r2dbc.sql.statements

import org.jetbrains.exposed.v1.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.sql.statements.UpsertStatement
import org.jetbrains.exposed.v1.sql.vendors.currentDialect

open class UpsertSuspendExecutable<Key : Any>(
    override val statement: UpsertStatement<Key>
) : org.jetbrains.exposed.v1.r2dbc.sql.statements.InsertSuspendExecutable<Key, UpsertStatement<Key>>(statement) {
    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, true)
        }

        return super.prepared(transaction, sql)
    }
}
