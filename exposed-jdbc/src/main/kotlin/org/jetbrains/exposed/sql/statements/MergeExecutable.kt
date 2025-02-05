package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

abstract class MergeExecutable<S : MergeStatement>(
    override val statement: S
) : Executable<Int, S> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? {
        return executeUpdate()
    }
}

open class MergeTableExecutable(
    statement: MergeTableStatement
) : MergeExecutable<MergeTableStatement>(statement)

open class MergeSelectExecutable(
    statement: MergeSelectStatement
) : MergeExecutable<MergeSelectStatement>(statement)
