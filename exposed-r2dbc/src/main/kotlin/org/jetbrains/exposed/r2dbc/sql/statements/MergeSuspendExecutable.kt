package org.jetbrains.exposed.r2dbc.sql.statements

import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.MergeStatement

open class MergeSuspendExecutable<S : MergeStatement>(
    override val statement: S
) : SuspendExecutable<Int, S> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? {
        executeUpdate()
        return this.getResultRow()?.rowsUpdated()?.awaitFirstOrNull()?.toInt() ?: 0
    }
}
