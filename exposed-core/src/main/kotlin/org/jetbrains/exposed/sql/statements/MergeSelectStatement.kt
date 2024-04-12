package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryAlias
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

class MergeSelectStatement(
    table: Table,
    private val selectQuery: QueryAlias,
    val on: Op<Boolean>
) : MergeBaseStatement(table) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        return transaction.db.dialect.functionProvider.mergeSelect(table, selectQuery, transaction, whenBranches, on, prepared)
    }
}
