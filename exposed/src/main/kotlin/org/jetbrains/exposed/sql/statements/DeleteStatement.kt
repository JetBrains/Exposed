package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

open class DeleteStatement(val table: Table, val where: Op<Boolean>? = null, val isIgnore: Boolean = false, val limit: Int? = null, val offset: Int? = null): Statement<Int>(StatementType.DELETE, listOf(table)) {

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        transaction.entityCache.removeTablesReferrers(listOf(table))
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction): String =
        transaction.db.dialect.functionProvider.delete(isIgnore, table, where?.toSQL(QueryBuilder(true)), limit, transaction)

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        where?.toSQL(this)
        listOf(args)
    }

    companion object {
        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false, limit: Int? = null, offset: Int? = null): Int
            = DeleteStatement(table, op, isIgnore, limit, offset).execute(transaction) ?: 0

        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}
