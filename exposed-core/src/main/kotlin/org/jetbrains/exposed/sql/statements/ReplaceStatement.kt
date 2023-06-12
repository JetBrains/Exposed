package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider
import org.jetbrains.exposed.sql.vendors.h2Mode

/**
 * Represents the SQL command that either inserts a new row into a table, or, if insertion would violate a unique constraint,
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
            H2Dialect.H2CompatibilityMode.MySQL, H2Dialect.H2CompatibilityMode.MariaDB -> MysqlFunctionProvider()
            else -> dialect.functionProvider
        }
        return functionProvider.replace(table, values.unzip().first, valuesSql, transaction, prepared)
    }
}
