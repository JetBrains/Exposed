package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider

/**
 * Represents the SQL statement that either inserts a new row into a table, or updates the existing row if insertion would violate a unique constraint.
 *
 * @param table Table to either insert values into or update values from.
 * @param keys (optional) Columns to include in the condition that determines a unique constraint match. If no columns are provided,
 * primary keys will be used. If the table does not have any primary keys, the first unique index will be attempted.
 * @param onUpdate List of pairs of specific columns to update and the expressions to update them with.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param onUpdateExclude List of specific columns to exclude from updating.
 * If left null, all columns will be updated with the values provided for the insert.
 * @param where Condition that determines which rows to update, if a unique violation is found. This clause may not be supported by all vendors.
 */
open class UpsertStatement<Key : Any>(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdate: List<Pair<Column<*>, Expression<*>>>?,
    val onUpdateExclude: List<Column<*>>?,
    val where: Op<Boolean>?
) : InsertStatement<Key>(table) {

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
        return arguments?.map { args ->
            val builder = QueryBuilder(true)
            args.filter { (_, value) ->
                value != DefaultValueMarker
            }.forEach { (column, value) ->
                builder.registerArgument(column, value)
            }
            where?.toQueryBuilder(builder)
            builder.args
        } ?: emptyList()
    }
}
