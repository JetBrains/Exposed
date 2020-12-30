package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.ITable
import org.jetbrains.exposed.sql.Transaction

open class ReplaceStatement<Key:Any>(table: ITable) : InsertStatement<Key>(table) {
    override fun prepareSQL(transaction: Transaction): String = transaction.db.dialect.functionProvider.replace(table, arguments!!.first(), transaction)
}
