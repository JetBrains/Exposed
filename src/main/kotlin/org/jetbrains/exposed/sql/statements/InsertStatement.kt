package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement<Key:Any>(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Key? = null

    infix operator fun <T:Key> get(column: Column<T>): T = generatedKey as? T ?: error("No key generated")

    protected fun valuesAndDefaults(): Map<Column<*>, Any?> {
        val columnsWithNotNullDefault = targets.flatMap { it.columns }.filter {
            (it.dbDefaultValue != null || it.defaultValueFun != null) && !it.columnType.nullable && it !in values.keys
        }
        return values + columnsWithNotNullDefault.map { it to (it.defaultValueFun?.invoke() ?: DefaultValueMarker) }
    }
    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val values = valuesAndDefaults()
        return transaction.db.dialect.insert(isIgnore, table, values.map { it.key},
            "VALUES (${values.entries.joinToString {
                val (col, value) = it
                when (value) {
                    is Expression<*> -> value.toSQL(builder)
                    DefaultValueMarker -> col.dbDefaultValue!!.toSQL(builder)
                    else -> builder.registerArgument(col.columnType, value)
                }
            }})", transaction)
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        transaction.entityCache.removeTablesReferrers(listOf(table))
        return executeUpdate().apply {
            table.columns.firstOrNull { it.columnType.autoinc }?.let { column ->
                generatedKeys?.let { rs ->
                    if (rs.next()) {
                        generatedKey = column.columnType.valueFromDB(rs.getObject(1)) as Key
                    }
                }
            }
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = QueryBuilder(true).run {
        valuesAndDefaults().forEach {
            val value = it.value
            when (value) {
                is Expression<*> -> value.toSQL(this)
                DefaultValueMarker -> {}
                else -> this.registerArgument(it.key.columnType, value)
            }
        }
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }
}
