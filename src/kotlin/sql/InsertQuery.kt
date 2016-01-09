package kotlin.sql

import java.util.*

/**
 * isIgnore is supported for mysql only
 */
class InsertQuery(val table: Table, val isIgnore: Boolean = false, val isReplace: Boolean = false) {
    val values = LinkedHashMap<Column<*>, Any?>()
    var generatedKey: Int? = null

    operator fun <T> set(column: Column<T>, value: T) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }

        values.put(column, column.columnType.valueToDB(value))
    }

    infix operator fun get(column: Column<Int>): Int {
        return generatedKey ?: error("No key generated")
    }

/*
    fun get(column: Column<EntityID>): EntityID {
        return EntityID(generatedKey ?: error("No key generated"), null)
    }
*/

    fun execute(transaction: Transaction): Int {
        val builder = QueryBuilder(true)
        val ignore = if (isIgnore && transaction.db.vendor == DatabaseVendor.MySql) " IGNORE " else ""
        val insert = if (isReplace && transaction.db.vendor == DatabaseVendor.MySql) "REPLACE" else "INSERT"
        var sql = StringBuilder("$insert ${ignore}INTO ${transaction.identity(table)}")

        sql.append(" (")
        sql.append((values.map { transaction.identity(it.key) }).joinToString(", "))
        sql.append(") ")

        sql.append("VALUES (")
        sql.append((values.map { builder.registerArgument(it.value, it.key.columnType) }).joinToString(", "))

        sql.append(") ")

        if (isReplace && transaction.db.vendor == DatabaseVendor.H2 && transaction.db.vendorCompatibleWith() == DatabaseVendor.MySql) {
            sql.append("ON DUPLICATE KEY UPDATE ")
            sql.append(values.map { "${transaction.identity(it.key)}=${it.key.columnType.valueToString(it.value)}"}.joinToString(", "))
        }

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
