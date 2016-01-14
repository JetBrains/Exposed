package org.jetbrains.exposed.sql

import java.util.*

/**
 * @author max
 */
class ReplaceStatement(val table: Table) : UpdateBuilder() {
    var generatedKey: Int? = null

    infix operator fun get(column: Column<Int>): Int {
        return generatedKey ?: error("No key generated")
    }

    fun execute(transaction: Transaction): Int {
        val sql = transaction.db.dialect.replace(
                transaction.identity(table),
                values.map { transaction.identity(it.key) },
                values.map { it.key.columnType.valueToString(it.value) })

        val autoincs: List<String> = table.columns.filter { it.columnType.autoinc }.map { transaction.identity(it)}
        return QueryBuilder(false).executeUpdate(transaction, sql.toString(), autoincs) { rs ->
            if (rs.next()) {
                generatedKey = rs.getInt(1)
            }
        }
    }
}
