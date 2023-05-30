package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

/**
 * Represents the SQL command that either batch inserts new rows into a table, or, if insertions violate unique constraints,
 * first deletes the existing rows before inserting new rows.
 *
 * @param table Table to either insert values into or delete values from then insert into.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
open class BatchReplaceStatement(
    table: Table,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues) {
    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!.first()
        val valuesSql = values.toSqlString()
        val functionProvider = transaction.db.dialect.functionProvider
        return functionProvider.replace(table, values.unzip().first, valuesSql, transaction)
    }
}
