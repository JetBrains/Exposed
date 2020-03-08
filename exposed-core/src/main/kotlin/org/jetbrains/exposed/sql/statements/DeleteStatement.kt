package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

open class DeleteStatement(val table: Table, val where: Op<Boolean>? = null, val isIgnore: Boolean = false, val limit: Int? = null, val offset: Long? = null): Statement<Int>(StatementType.DELETE, listOf(table)) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction): String =
        transaction.db.dialect.functionProvider.delete(isIgnore, table, where?.let { QueryBuilder(true).append(it).toString() }, limit, transaction)

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        where?.toQueryBuilder(this)
        listOf(args)
    }

    companion object {
        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false, limit: Int? = null, offset: Long? = null): Int
            = DeleteStatement(table, op, isIgnore, limit, offset).execute(transaction) ?: 0

        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}
