package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * @author max
 */
class ReplaceStatement<Key:Any>(table: Table) : InsertStatement<Key>(table) {

    override fun prepareSQL(transaction: Transaction): String = currentDialect.replace(table, arguments!!.first(), transaction)
}
