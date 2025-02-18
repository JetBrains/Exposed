package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi

open class InsertSelectSuspendExecutable(
    override val statement: InsertSelectStatement
) : SuspendExecutable<Int, InsertSelectStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? = executeUpdate()
}
