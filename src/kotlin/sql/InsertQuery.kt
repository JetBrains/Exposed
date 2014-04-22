package kotlin.sql

import java.util.LinkedHashMap
import kotlin.dao.EntityID

/**
 * isIgnore is supported for mysql only
 */
class InsertQuery(val table: Table, val isIgnore: Boolean = false) {
    val values = LinkedHashMap<Column<*>, Any?>()
    var generatedKey: Int? = null

    fun <T> set(column: Column<T>, value: T) {
        if (values containsKey column) {
            error("$column is already initialized")
        }

        values.put(column, if (value == null) null else {
            when(column.columnType) {
                is EnumerationColumnType<*> -> (value as Enum<*>).ordinal()
                is EntityIDColumnType -> (value as EntityID).value
                else -> value
            }
        })
    }

    fun get(column: Column<Int>): Int {
        return generatedKey ?: error("No key generated")
    }

/*
    fun get(column: Column<EntityID>): EntityID {
        return EntityID(generatedKey ?: error("No key generated"), null)
    }
*/

    fun execute(session: Session): Int {
        val builder = QueryBuilder(true)
        val ignore = if (isIgnore) " IGNORE " else ""
        var sql = StringBuilder("INSERT ${ignore}INTO ${session.identity(table)}")

        sql.append(" (")
        sql.append((values map { session.identity(it.key) }).makeString(", ", "", ""))
        sql.append(") ")

        sql.append("VALUES (")
        sql.append((values map { builder.registerArgument(it.value, it.key.columnType) }). makeString(", ", "", ""))

        sql.append(") ")
        try {
            val autoincs: List<String> = table.columns.filter { it.columnType.autoinc } map {session.identity(it)}
            return builder.executeUpdate(session, sql.toString(), autoincs) { rs ->
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
