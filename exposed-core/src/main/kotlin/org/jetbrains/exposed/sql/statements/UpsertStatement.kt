package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.*

open class UpsertStatement<Key : Any>(
    table: Table,
    vararg val keys: Column<*>,
    val onUpdate: List<Pair<Column<*>, Expression<*>>>?,
    val where: Op<Boolean>?
) : InsertStatement<Key>(table) {

    override fun prepareSQL(transaction: Transaction): String {
        val functionProvider = when (val dialect = transaction.db.dialect) {
            is H2Dialect -> when (dialect.h2Mode) {
                H2Dialect.H2CompatibilityMode.MariaDB, H2Dialect.H2CompatibilityMode.MySQL -> MysqlFunctionProvider()
                else -> H2FunctionProvider
            }
            else -> dialect.functionProvider
        }
        return functionProvider.upsert(table, arguments!!.first(), onUpdate, where, transaction, *keys)
    }
}
