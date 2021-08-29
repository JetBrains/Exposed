package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*

open class BatchReplaceStatement(
    table: Table,
    shouldReturnGeneratedValues: Boolean = true
): BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues) {
    override fun prepareSQL(transaction: Transaction): String = transaction.db.dialect.functionProvider.replace(table, arguments!!.first(), transaction)
}
