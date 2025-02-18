package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi

open class UpdateSuspendExecutable(
    override val statement: UpdateStatement
) : SuspendExecutable<Int, UpdateStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        return executeUpdate()
    }
}
