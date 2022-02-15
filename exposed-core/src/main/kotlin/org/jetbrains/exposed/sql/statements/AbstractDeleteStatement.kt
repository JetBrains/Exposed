package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.render.RenderDeleteSQLCallbacks
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

abstract class AbstractDeleteStatement<STATEMENT_RETURN>(
    val table: Table,
    var where: Op<Boolean>? = null,
    val isIgnore: Boolean = false,
    val limit: Int? = null,
    val offset: Long? = null,
    var renderSqlCallback: RenderDeleteSQLCallbacks = RenderDeleteSQLCallbacks.Noop
) : Statement<STATEMENT_RETURN>(StatementType.DELETE, listOf(table)) {

    override fun prepareSQL(transaction: Transaction): String {
        val where =  where?.let { QueryBuilder(true).append(it).toString() }
        return transaction.db.dialect.functionProvider.delete(isIgnore, table, where, limit, transaction, renderSqlCallback)
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        where?.toQueryBuilder(this)
        listOf(args)
    }
}

open class DeleteStatement(
    table: Table,
    where: Op<Boolean>? = null,
    isIgnore: Boolean = false,
    limit: Int? = null,
    offset: Long? = null
) : AbstractDeleteStatement<Int>(table, where, isIgnore, limit, offset) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        return executeUpdate()
    }

    companion object {
        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false, limit: Int? = null, offset: Long? = null): Int =
            DeleteStatement(table, op, isIgnore, limit, offset).execute(transaction) ?: 0

        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}
