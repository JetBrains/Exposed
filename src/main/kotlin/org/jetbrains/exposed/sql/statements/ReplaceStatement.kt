package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction

/**
 * @author max
 */
class ReplaceStatement<Key:Any>(table: Table) : InsertStatement<Key>(table) {

    override fun prepareSQL(transaction: Transaction): String = transaction.db.dialect.replace(table, values.toList(), transaction)
}
