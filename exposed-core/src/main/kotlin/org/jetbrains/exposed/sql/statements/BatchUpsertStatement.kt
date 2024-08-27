package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * Represents the SQL statement that either batch inserts new rows into a table, or updates the existing rows if insertions violate unique constraints.
 *
 * @param table Table to either insert values into or update values from.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found. This clause may not be supported by all vendors.
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs) should be returned.
 * See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
open class BatchUpsertStatement(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdateExclude: List<Column<*>>?,
    val where: Op<Boolean>?,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues), UpsertBuilder {
    @Deprecated(
        "This constructor with `onUpdate` that takes a List may be removed in future releases.",
        level = DeprecationLevel.WARNING
    )
    constructor(
        table: Table,
        vararg keys: Column<*>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        onUpdateExclude: List<Column<*>>?,
        where: Op<Boolean>?,
        shouldReturnGeneratedValues: Boolean
    ) : this(table, keys = keys, onUpdateExclude, where, shouldReturnGeneratedValues) {
        onUpdate?.let {
            this.onUpdate = it
            updateValues.putAll(it)
        }
    }

    @Deprecated("This property will be removed in future releases.", level = DeprecationLevel.WARNING)
    var onUpdate: List<Pair<Column<*>, Expression<*>>>? = null
        private set

    internal val updateValues: MutableMap<Column<*>, Any?> = LinkedHashMap()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val dialect = transaction.db.dialect
        val functionProvider = UpsertBuilder.getFunctionProvider(dialect)
        val keyColumns = if (functionProvider is MysqlFunctionProvider) keys.toList() else getKeyColumns(keys = keys)
        val insertValues = arguments!!.first()
        val insertValuesSql = insertValues.toSqlString(prepared)
        val updateExpressions = updateValues.takeIf { it.isNotEmpty() }?.toList()
            ?: getUpdateExpressions(insertValues.unzip().first, onUpdateExclude, keyColumns)
        return functionProvider.upsert(table, insertValues, insertValuesSql, updateExpressions, keyColumns, where, transaction)
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
