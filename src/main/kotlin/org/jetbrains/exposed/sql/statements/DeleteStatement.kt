package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

class DeleteStatement(val table: Table, val where: Op<Boolean>? = null, val isIgnore: Boolean = false): Statement<Int>(StatementType.DELETE, listOf(table)) {

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        transaction.entityCache.removeTablesReferrers(listOf(table))
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction): String = transaction.db.dialect.delete(isIgnore, table, where?.toSQL(QueryBuilder(true)), transaction)

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = QueryBuilder(true).run {
        where?.toSQL(this)
        listOf(args)
    }

    companion object {

        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false): Int
            = DeleteStatement(table, op, isIgnore).execute(transaction) ?: 0

        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}
