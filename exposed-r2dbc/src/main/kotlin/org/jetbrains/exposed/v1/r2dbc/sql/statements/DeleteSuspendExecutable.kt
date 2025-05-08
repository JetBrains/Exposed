package org.jetbrains.exposed.v1.r2dbc.sql.statements

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.sql.statements.DeleteStatement

open class DeleteSuspendExecutable(
    override val statement: DeleteStatement
) : SuspendExecutable<Int, DeleteStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        executeUpdate()
        return this.getResultRow()?.rowsUpdated()?.singleOrNull() ?: 0
    }
}
