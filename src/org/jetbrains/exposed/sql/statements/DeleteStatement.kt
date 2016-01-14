package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

class DeleteStatement(val table: Table, val where: Op<Boolean>? = null, val isIgnore: Boolean = false): Statement<Int>(StatementType.DELETE, listOf(table)) {

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int = executeUpdate().apply {
        if (where == null) {
            transaction.flushCache()
        } else {
            EntityCache.getOrCreate(transaction).removeTablesReferrers(listOf(table))
        }
    }

    override fun prepareSQL(transaction: Transaction): String = buildString {
        val ignore = if (isIgnore && transaction.db.vendor == DatabaseVendor.MySql) "IGNORE" else ""
        append("DELETE $ignore FROM ${transaction.identity(table)}")
        where?.let {
            append(" WHERE ${it.toSQL(QueryBuilder(true))}")
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = QueryBuilder(true).run {
        where?.toSQL(this)
        listOf(args)
    }

    companion object {

        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false): Int
            = DeleteStatement(table, op, isIgnore).execute(transaction).first ?: 0

        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction).first ?: 0
    }
}
