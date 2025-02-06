package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi

abstract class MergeExecutable<S : MergeStatement>(
    override val statement: S
) : Executable<Int, S> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? {
        return executeUpdate()
    }
}

open class MergeTableExecutable(
    statement: MergeTableStatement
) : MergeExecutable<MergeTableStatement>(statement)

open class MergeSelectExecutable(
    statement: MergeSelectStatement
) : MergeExecutable<MergeSelectStatement>(statement)
