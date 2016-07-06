package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Any? = null

    @Suppress("UNCHECKED_CAST")
    infix operator fun <T> get(column: Column<T>): T = if (column.columnType.autoinc) column.columnType.valueFromDB(generatedKey ?: error("No key generated")) as T else error("Column isn't autoInc")

    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        return transaction.db.dialect.insert(isIgnore, table, values.map { it.key},
            "VALUES (${values.entries.joinToString {
                val (col, value) = it
                when (value) {
                    is Expression<*> -> value.toSQL(builder)
                    else -> builder.registerArgument(value, col.columnType)
                }
            }})", transaction)
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        EntityCache.getOrCreate(transaction).removeTablesReferrers(listOf(table))
        return executeUpdate().apply {
            if (table.columns.any { it.columnType.autoinc }) {
                generatedKeys?.let { rs ->
                    if (rs.next()) {
                        generatedKey = rs.getObject(1)
                    }
                }
            }
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = QueryBuilder(true).run {
        values.forEach {
            val value = it.value
            when (value) {
                is Expression<*> -> value.toSQL(this)
                else -> this.registerArgument(value, it.key.columnType)
            }
        }
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }
}
