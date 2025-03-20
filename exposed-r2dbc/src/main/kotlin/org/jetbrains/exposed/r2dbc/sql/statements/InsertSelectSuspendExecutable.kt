package org.jetbrains.exposed.r2dbc.sql.statements

import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.InsertSelectStatement

open class InsertSelectSuspendExecutable(
    override val statement: InsertSelectStatement
) : SuspendExecutable<Int, InsertSelectStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? {
        executeUpdate()

        return this.getResultRow()?.rowsUpdated()?.awaitFirstOrNull()?.toInt()
    }
}
