package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect

open class UpsertExecutable<Key : Any>(
    override val statement: UpsertStatement<Key>
) : InsertExecutable<Key, UpsertStatement<Key>>(statement) {
    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, true)
        }

        return super.prepared(transaction, sql)
    }
}
