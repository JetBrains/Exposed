package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

/**
 * Represents the SQL statement that uses data retrieved from a [selectQuery] to insert new rows into a table.
 *
 * @param columns Columns to insert the values into.
 * @param selectQuery Source SELECT query that provides the values to insert.
 * @param isIgnore Whether to ignore errors or not.
 * **Note** [isIgnore] is not supported by all vendors. Please check the documentation.
 */
open class InsertSelectStatement(
    val columns: List<Column<*>>,
    val selectQuery: AbstractQuery<*>,
    val isIgnore: Boolean = false
) : Statement<Int>(StatementType.INSERT, listOf(columns.first().table)) {

    init {
        if (columns.isEmpty()) error("Can't insert without provided columns")
        val tables = columns.distinctBy { it.table }
        if (tables.count() > 1) error("Can't insert to different tables ${tables.joinToString { it.name }} from single select")
        if (columns.size != selectQuery.set.fields.size) error("Columns count doesn't equal to query columns count")
    }

    override suspend fun PreparedStatementApi.executeInternal(transaction: Transaction): Int? = executeUpdate()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = selectQuery.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String =
        transaction.db.dialect.functionProvider.insert(isIgnore, targets.single(), columns, selectQuery.prepareSQL(transaction, prepared), transaction)
}
