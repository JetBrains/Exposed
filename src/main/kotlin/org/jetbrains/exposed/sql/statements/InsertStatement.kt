package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement<Key:Any>(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Key? = null

    infix operator fun <T:Key> get(column: Column<T>): T = generatedKey as? T ?: error("No key generated")

    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        return transaction.db.dialect.insert(isIgnore, table, values.map { it.key},
            "VALUES (${values.entries.joinToString {
                val (col, value) = it
                when (value) {
                    is Expression<*> -> value.toSQL(builder)
                    else -> builder.registerArgument(col.columnType, value)
                }
            }})", transaction)
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        EntityCache.getOrCreate(transaction).removeTablesReferrers(listOf(table))
        return executeUpdate().apply {
            table.columns.firstOrNull { it.columnType.autoinc }?.let { column ->
                generatedKeys?.let { rs ->
                    if (rs.next()) {
                        generatedKey = column.columnType.readObject(rs, 1) as Key
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
                else -> this.registerArgument(it.key.columnType, value)
            }
        }
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }
}
