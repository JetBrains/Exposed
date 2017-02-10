package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

class InsertSelectStatement(val columns: List<Column<*>>, val selectQuery: Query, val isIgnore: Boolean = false): Statement<Int>(StatementType.INSERT, listOf(columns.first().table)) {

    init {
        if (columns.isEmpty()) error("Can't insert without provided columns")
        val tables = columns.distinctBy { it.table }
        if (tables.count() > 1) error("Can't insert to different tables ${tables.joinToString { it.name }} from single select")
        if (columns.size != selectQuery.set.fields.size) error("Columns count doesn't equal to query columns count")
    }

    var generatedKey: Int? = null

    operator fun get(column: Column<Int>): Int = generatedKey ?: error("Statement is not executed or table has not any auto-generated fields")

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int? = executeUpdate().apply {
        if (targets.single().columns.any { it.columnType.isAutoInc }) {
            generatedKey = generatedKeys?.let { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                } else if (!isIgnore) {
                    throw IllegalStateException("No key generated after statement: ${prepareSQL(transaction)}")
                } else {
                    null
                }
            }
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = emptyList()

    override fun prepareSQL(transaction: Transaction): String {
        return transaction.db.dialect.insert(isIgnore, targets.single(), columns, selectQuery.prepareSQL(QueryBuilder(false)), transaction)
    }
}
