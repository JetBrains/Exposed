package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param table Table to either insert values into or update values from.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found. This clause may not be supported by all vendors.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
open class BatchUpsertStatement(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdate: List<Pair<Column<*>, Expression<*>>>?,
    val onUpdateExclude: List<Column<*>>?,
    val where: Op<Boolean>?,
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
        return functionProvider.upsert(table, arguments!!.first(), onUpdate, onUpdateExclude, where, transaction, keys = keys)
    }

    override fun arguments(): List<Iterable<Pair<IColumnType<*>, Any?>>> {
        val whereArgs = QueryBuilder(true).apply {
            where?.toQueryBuilder(this)
        }.args
        return super.arguments().map {
            it + whereArgs
        }
    }

    override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, shouldReturnGeneratedValues)
        }

        return super.prepared(transaction, sql)
    }

    override fun isColumnValuePreferredFromResultSet(column: Column<*>, value: Any?): Boolean {
        return isEntityIdClientSideGeneratedUUID(column) ||
            super.isColumnValuePreferredFromResultSet(column, value)
    }
}
