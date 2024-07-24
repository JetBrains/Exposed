package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
 *
 * @param table Table to either insert values into or update values from.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate Lambda accepting a list of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found. This clause may not be supported by all vendors.
 */
open class UpsertStatement<Key : Any>(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdate: MutableList<Pair<Column<*>, Expression<*>>>?,
    val onUpdateExclude: List<Column<*>>?,
    val where: Op<Boolean>?
) : InsertStatement<Key>(table), UpsertBuilder {

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val dialect = transaction.db.dialect
        val functionProvider = UpsertBuilder.getFunctionProvider(dialect)
        val keyColumns = if (functionProvider is MysqlFunctionProvider) {
            keys.toList()
        } else {
            getKeyColumns(table, keys = keys)
        }
        val insertValues = arguments!!.first()
        val insertValuesSql = insertValues.toSqlString(prepared)
        val updateExpressions = onUpdate ?: getUpdateColumns(
            insertValues.unzip().first, onUpdateExclude, keyColumns
        )
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

    override fun isColumnValuePreferredFromResultSet(column: Column<*>, value: Any?): Boolean {
        return isEntityIdClientSideGeneratedUUID(column) ||
            super.isColumnValuePreferredFromResultSet(column, value)
    }

    override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi {
        // We must return values from upsert because returned id could be different depending on insert or upsert happened
        if (!currentDialect.supportsOnlyIdentifiersInGeneratedKeys) {
            return transaction.connection.prepareStatement(sql, true)
        }

        return super.prepared(transaction, sql)
    }
}

/**
 * Common interface for building SQL statements that either insert a new row into a table,
 * or update the existing row if insertion would violate a unique constraint.
 */
internal interface UpsertBuilder {
    /** Returns the columns to be used in the conflict condition of an upsert statement. */
    fun getKeyColumns(table: Table, vararg keys: Column<*>): List<Column<*>> {
        return keys.toList().ifEmpty {
            table.primaryKey?.columns?.toList() ?: table.indices.firstOrNull { it.unique }?.columns
        } ?: emptyList()
    }

    /** Returns the columns to be used in the update clause of an upsert statement, along with their insert column reference. */
    fun getUpdateColumns(
        dataColumns: List<Column<*>>,
        toExclude: List<Column<*>>?,
        keyColumns: List<Column<*>>?
    ): List<Pair<Column<*>, Expression<*>>> {
        val updateColumns = toExclude?.let { dataColumns - it.toSet() } ?: dataColumns
        val updateColumnsWithoutKeys = keyColumns?.let { keys ->
            updateColumns.filter { it !in keys }.ifEmpty { updateColumns }
        } ?: updateColumns
        return updateColumnsWithoutKeys.zip(updateColumnsWithoutKeys.map { it.asForInsert() })
    }

    /**
     * Specifies that this column should be updated using the same values that would be inserted if there was
     * no violation of a unique constraint in an upsert statement.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testUpsertWithManualUpdateUsingInsertValues
     */
    fun <T> Column<T>.asForInsert(): ExpressionWithColumnType<T> = AsForInsert(this, this.columnType)

    /**
     * Represents the SQL syntax for a [column] that should use the same values that would be inserted if there was
     * no violation of a unique constraint in an upsert statement.
     */
    private class AsForInsert<T>(
        val column: Column<T>,
        override val columnType: IColumnType<T & Any>
    ) : ExpressionWithColumnType<T>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            val transaction = TransactionManager.current()
            val functionProvider = getFunctionProvider(transaction.db.dialect)
            functionProvider.asForInsert(transaction.identity(column), queryBuilder)
        }
    }

    companion object {
        /** Returns the [FunctionProvider] for valid upsert statement syntax. */
        fun getFunctionProvider(dialect: DatabaseDialect): FunctionProvider = when (dialect) {
            is H2Dialect -> when (dialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.MariaDB, H2Dialect.H2CompatibilityMode.MySQL -> MysqlFunctionProvider.INSTANCE
                else -> H2FunctionProvider
            }
            else -> dialect.functionProvider
        }
    }
}
