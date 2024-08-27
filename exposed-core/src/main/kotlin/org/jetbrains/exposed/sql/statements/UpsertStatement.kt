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
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found. This clause may not be supported by all vendors.
 */
open class UpsertStatement<Key : Any>(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdateExclude: List<Column<*>>?,
    val where: Op<Boolean>?
) : InsertStatement<Key>(table), UpsertBuilder {
    @Deprecated(
        "This constructor with `onUpdate` that takes a List may be removed in future releases.",
        level = DeprecationLevel.WARNING
    )
    constructor(
        table: Table,
        vararg keys: Column<*>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        onUpdateExclude: List<Column<*>>?,
        where: Op<Boolean>?
    ) : this(table, keys = keys, onUpdateExclude, where) {
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
sealed interface UpsertBuilder {
    /**
     * Calls the specified function [onUpdate] with an [UpdateStatement] as its argument,
     * allowing values to be stored as part of the UPDATE clause of the upsert statement associated with this builder.
     */
    fun storeUpdateValues(onUpdate: UpsertBuilder.(UpdateStatement) -> Unit) {
        val arguments = UpdateStatement((this as InsertStatement<*>).table, null).apply {
            onUpdate.invoke(this@UpsertBuilder, this)
        }.firstDataSet
        when (this) {
            is UpsertStatement<*> -> updateValues.putAll(arguments)
            is BatchUpsertStatement -> updateValues.putAll(arguments)
        }
    }

    /**
     * Specifies that this column should be updated using the same values that would be inserted if there was
     * no violation of a unique constraint in an upsert statement.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.UpsertTests.testUpsertWithManualUpdateUsingInsertValues
     */
    fun <T> insertValue(column: Column<T>): ExpressionWithColumnType<T> = InsertValue(column, column.columnType)

    private class InsertValue<T>(
        val column: Column<T>,
        override val columnType: IColumnType<T & Any>
    ) : ExpressionWithColumnType<T>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            val transaction = TransactionManager.current()
            val functionProvider = getFunctionProvider(transaction.db.dialect)
            functionProvider.insertValue(transaction.identity(column), queryBuilder)
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

/** Returns the columns to be used in the conflict condition of an upsert statement. */
internal fun UpsertBuilder.getKeyColumns(vararg keys: Column<*>): List<Column<*>> {
    this as InsertStatement<*>
    return keys.toList().ifEmpty {
        table.primaryKey?.columns?.toList() ?: table.indices.firstOrNull { it.unique }?.columns
    } ?: emptyList()
}

/** Returns the expressions to be used in the update clause of an upsert statement, along with their insert column reference. */
internal fun UpsertBuilder.getUpdateExpressions(
    dataColumns: List<Column<*>>,
    toExclude: List<Column<*>>?,
    keyColumns: List<Column<*>>?
): List<Pair<Column<*>, Any?>> {
    val updateColumns = toExclude?.let { dataColumns - it } ?: dataColumns
    val updateColumnsWithoutKeys = keyColumns?.let { keys ->
        updateColumns.filter { it !in keys }.ifEmpty { updateColumns }
    } ?: updateColumns
    return updateColumnsWithoutKeys.zip(updateColumnsWithoutKeys.map { insertValue(it) })
}
