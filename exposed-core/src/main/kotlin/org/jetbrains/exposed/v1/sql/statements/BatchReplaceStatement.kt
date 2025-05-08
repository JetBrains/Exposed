package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.Transaction
import org.jetbrains.exposed.v1.sql.vendors.H2Dialect
import org.jetbrains.exposed.v1.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.v1.sql.vendors.h2Mode

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or, if insertions violate unique constraints,
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
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val values = arguments!!.first()
        val valuesSql = values.toSqlString(prepared)
        val dialect = transaction.db.dialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.MySQL, H2Dialect.H2CompatibilityMode.MariaDB -> MysqlFunctionProvider.INSTANCE
            else -> dialect.functionProvider
        }
        return functionProvider.replace(table, values.unzip().first, valuesSql, transaction, prepared)
    }
}
