package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.AbstractQuery
import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.Transaction
import org.jetbrains.exposed.v1.sql.vendors.H2Dialect
import org.jetbrains.exposed.v1.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.v1.sql.vendors.h2Mode

/**
 * Represents the SQL statement that either inserts a new row into a table, or, if insertion would violate a unique constraint,
 * first deletes the existing row before inserting a new row.
 *
 * @param table Table to either insert values into or delete values from then insert into.
 */
open class ReplaceStatement<Key : Any>(table: Table) : InsertStatement<Key>(table) {
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

/**
 * Represents the SQL statement that uses data retrieved from a [selectQuery] to either insert a new row into a table,
 * or, if insertion would violate a unique constraint, first delete the existing row before inserting a new row.
 *
 * @param columns Columns to either insert values into or delete values from then insert into.
 * @param selectQuery Source SELECT query that provides the values to insert.
 */
open class ReplaceSelectStatement(
    columns: List<Column<*>>,
    selectQuery: AbstractQuery<*>
) : InsertSelectStatement(columns, selectQuery) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val querySql = selectQuery.prepareSQL(transaction, prepared)
        val dialect = transaction.db.dialect
        val functionProvider = when (dialect.h2Mode) {
            H2Dialect.H2CompatibilityMode.MySQL, H2Dialect.H2CompatibilityMode.MariaDB -> MysqlFunctionProvider.INSTANCE
            else -> dialect.functionProvider
        }
        return functionProvider.replace(targets.single(), columns, querySql, transaction, prepared)
    }
}
