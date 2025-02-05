package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class BatchUpdateExecutable(
    override val statement: BatchUpdateStatement
) : UpdateExecutable(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return if (this@BatchUpdateExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
    }
}
