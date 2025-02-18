package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class BatchUpdateBlockingExecutable(
    override val statement: BatchUpdateStatement
) : UpdateBlockingExecutable(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return if (this@BatchUpdateBlockingExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
    }
}
