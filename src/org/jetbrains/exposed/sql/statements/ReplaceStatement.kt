package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*

/**
 * @author max
 */
class ReplaceStatement(table: Table) : InsertStatement(table) {

    override fun prepareSQL(transaction: Transaction): String = transaction.db.dialect.replace(table, values.toList(), transaction)
}
