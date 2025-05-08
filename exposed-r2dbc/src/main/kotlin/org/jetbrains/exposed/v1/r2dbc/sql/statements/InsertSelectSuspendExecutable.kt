package org.jetbrains.exposed.v1.r2dbc.sql.statements

import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.sql.statements.InsertSelectStatement

open class InsertSelectSuspendExecutable(
    override val statement: InsertSelectStatement
) : SuspendExecutable<Int, InsertSelectStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? {
        executeUpdate()

        return try {
            this.getResultRow()?.rowsUpdated()?.reduce(Int::plus) ?: 0
        } catch (_: NoSuchElementException) { // flow might be empty
            0
        }
    }
}
