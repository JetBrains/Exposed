package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.ITransaction

open class ReplaceStatement<Key:Any>(table: Table) : InsertStatement<Key>(table) {
    override fun prepareSQL(transaction: ITransaction): String = transaction.db.dialect.functionProvider.replace(table, arguments!!.first(), transaction)
}
