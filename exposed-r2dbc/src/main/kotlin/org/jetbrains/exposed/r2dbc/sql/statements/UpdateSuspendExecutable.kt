package org.jetbrains.exposed.r2dbc.sql.statements

import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.statements.UpdateStatement

open class UpdateSuspendExecutable(
    override val statement: UpdateStatement
) : SuspendExecutable<Int, UpdateStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        executeUpdate()

        return this.getResultRow()?.rowsUpdated()?.awaitFirstOrNull()?.toInt() ?: 0
    }
}
