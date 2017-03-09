package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement<Key:Any>(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Key? = null

    infix operator fun <T:Key> get(column: Column<T>): T = generatedKey as? T ?: error("No key generated")

    open protected fun generatedKeyFun(rs: ResultSet, inserted: Int) : Key? {
        return table.columns.firstOrNull { it.columnType.autoinc }?.let { column ->
            if (rs.next()) {
                @Suppress("UNCHECKED_CAST")
                column.columnType.valueFromDB(rs.getObject(1)) as? Key
            } else null
        }
    }

    protected fun valuesAndDefaults(): Map<Column<*>, Any?> {
        val columnsWithNotNullDefault = targets.flatMap { it.columns }.filter {
            (it.dbDefaultValue != null || it.defaultValueFun != null) && !it.columnType.nullable && it !in values.keys
        }
        return values + columnsWithNotNullDefault.map { it to (it.defaultValueFun?.invoke() ?: DefaultValueMarker) }
    }
    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val values = valuesAndDefaults()
        val sql = if(values.isEmpty()) ""
        else values.entries.joinToString(prefix = "VALUES (", postfix = ")") {
            val (col, value) = it
            when (value) {
                is Expression<*> -> value.toSQL(builder)
                DefaultValueMarker -> col.dbDefaultValue!!.toSQL(builder)
                else -> builder.registerArgument(col.columnType, value)
            }
        }
        return transaction.db.dialect.insert(isIgnore, table, values.map { it.key }, sql, transaction)
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        transaction.flushCache()
        transaction.entityCache.removeTablesReferrers(listOf(table))
        val inserted = if (arguments().count() > 1) executeBatch().sum() else executeUpdate()
        return inserted.apply {
            generatedKeys?.let {
                generatedKey = generatedKeyFun(it, this)
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
