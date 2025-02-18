package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi

open class DeleteSuspendExecutable(
    override val statement: DeleteStatement
) : SuspendExecutable<Int, DeleteStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        return executeUpdate()
    }
}
