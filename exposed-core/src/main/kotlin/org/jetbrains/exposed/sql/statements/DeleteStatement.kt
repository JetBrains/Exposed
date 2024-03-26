package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

/**
 * Represents the SQL statement that deletes one or more rows of a table.
 *
 * @param table Table to delete rows from.
 * @param where Condition that determines which rows to delete.
 * @param isIgnore Whether to ignore errors or not.
 * **Note** [isIgnore] is not supported by all vendors. Please check the documentation.
 * @param limit Maximum number of rows to delete.
 * @param offset The number of rows to skip.
 */
open class DeleteStatement(
    val table: Table,
    val where: Op<Boolean>? = null,
    val isIgnore: Boolean = false,
    val limit: Int? = null,
    val offset: Long? = null
) : Statement<Int>(StatementType.DELETE, listOf(table)) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String =
        transaction.db.dialect.functionProvider.delete(isIgnore, table, where?.let { QueryBuilder(prepared).append(it).toString() }, limit, transaction)

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = QueryBuilder(true).run {
        where?.toQueryBuilder(this)
        listOf(args)
    }

    companion object {
        /**
         * Creates a [DeleteStatement] that deletes only rows in [table] that match the provided [op].
         *
         * @return Count of deleted rows.
         */
        fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false, limit: Int? = null, offset: Long? = null): Int =
            DeleteStatement(table, op, isIgnore, limit, offset).execute(transaction) ?: 0

        /**
         * Creates a [DeleteStatement] that deletes all rows in [table].
         *
         * @return Count of deleted rows.
         */
        fun all(transaction: Transaction, table: Table): Int = DeleteStatement(table).execute(transaction) ?: 0
    }
}
