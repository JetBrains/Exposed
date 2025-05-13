package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

open class InsertSelectSuspendExecutable(
    override val statement: org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
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
