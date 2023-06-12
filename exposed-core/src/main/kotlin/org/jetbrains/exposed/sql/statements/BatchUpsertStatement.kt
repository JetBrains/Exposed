package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider

/**
 * Represents the SQL command that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * **Note**: Unlike `UpsertStatement`, `BatchUpsertStatement` does not include a `where` parameter. Please log a feature request
 * on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=EXPOSED&c=Type%20Feature&draftId=25-4449790) if a use-case requires inclusion of a `where` clause.
 *
 * @param table Table to either insert values into or update values from.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
open class BatchUpsertStatement(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdate: List<Pair<Column<*>, Expression<*>>>?,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues) {

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val functionProvider = when (val dialect = transaction.db.dialect) {
            is H2Dialect -> when (dialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.MariaDB, H2Dialect.H2CompatibilityMode.MySQL -> MysqlFunctionProvider()
                else -> H2FunctionProvider
            }
            else -> dialect.functionProvider
        }
        return functionProvider.upsert(table, arguments!!.first(), onUpdate, null, transaction, *keys)
    }
}
