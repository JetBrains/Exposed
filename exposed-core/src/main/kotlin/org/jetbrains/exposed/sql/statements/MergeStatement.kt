package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

open class MergeStatement(
    dest: Table,
    private val source: Table,
    private val on: Op<Boolean>
) : MergeBaseStatement(dest) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val result = transaction.db.dialect.functionProvider.merge(table, source, transaction, whenBranches, on)
        return result
    }
}
