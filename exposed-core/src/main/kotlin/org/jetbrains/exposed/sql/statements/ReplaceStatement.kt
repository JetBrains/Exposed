package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

/**
 * Represents the SQL command that either inserts a new row into a table, or, if insertion would violate a unique constraint,
 * first deletes the existing row before inserting a new row.
 *
 * @param table Table to either insert values into or delete values from then insert into.
 */
open class ReplaceStatement<Key : Any>(table: Table) : InsertStatement<Key>(table) {
    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!.first()
        val valuesSql = values.toSqlString()
        val functionProvider = transaction.db.dialect.functionProvider
        return functionProvider.replace(table, values.unzip().first, valuesSql, transaction)
    }
}
