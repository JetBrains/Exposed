package org.jetbrains.exposed.sql

/**
 * isIgnore is supported for mysql only
 */
class InsertStatement(val table: Table, val isIgnore: Boolean = false): UpdateBuilder() {
    var generatedKey: Int? = null

    infix operator fun get(column: Column<Int>): Int {
        return generatedKey ?: error("No key generated")
    }

    fun execute(transaction: Transaction): Int {
        val builder = QueryBuilder(true)
        val sql = transaction.db.dialect.insert(isIgnore,
                transaction.identity(table),
                values.map { transaction.identity(it.key) },
                "VALUES (${values.map { builder.registerArgument(it.value, it.key.columnType) }.joinToString()})")
        try {
            val autoincs: List<String> = table.columns.filter { it.columnType.autoinc }.map { transaction.identity(it)}
            return builder.executeUpdate(transaction, sql.toString(), autoincs) { rs ->
                if (rs.next()) {
                    generatedKey = rs.getInt(1)
                }
            }
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }
    }
}
