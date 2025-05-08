package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.MysqlFunctionProvider

/**
 * Represents the SQL statement that batch updates rows of a table.
 *
 * @param table Identity table to update values from.
 */
open class BatchUpdateStatement(
    table: Table,
    val limit: Int?,
    val where: Op<Boolean>?
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues = false) {

    override fun <T, S : T?> update(column: Column<T>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val dialect = transaction.db.dialect
        val functionProvider = UpsertBuilder.getFunctionProvider(dialect)
        val insertValues = arguments!!.first()
        return functionProvider.update(table, insertValues, limit, where, transaction)
    }

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int = if (data.size == 1) executeUpdate() else executeBatch().sum()

    override fun arguments(): List<Iterable<Pair<IColumnType<*>, Any?>>> {
        val whereArgs = QueryBuilder(true).apply {
            where?.toQueryBuilder(this)
        }.args
        return super.arguments().map {
            it + whereArgs
        }
    }
}
