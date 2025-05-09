package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.statements.DeleteStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

open class DeleteSuspendExecutable(
    override val statement: DeleteStatement
) : SuspendExecutable<Int, DeleteStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        executeUpdate()
        return this.getResultRow()?.rowsUpdated()?.singleOrNull() ?: 0
    }
}
