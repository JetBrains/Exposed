package kotlin.sql

import java.util.LinkedHashMap

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
            if (column.columnType is EnumerationColumnType<*>) (value as Enum<*>).ordinal() else value
        })
    }

    fun get(column: Column<Int>): Int {
        return generatedKey ?: error("No key generated")
    }

    fun execute(session: Session) {
        val builder = QueryBuilder(true)
        val ignore = if (isIgnore) " IGNORE " else ""
        var sql = StringBuilder("INSERT ${ignore}INTO ${session.identity(table)}")

        sql.append(" (")
        sql.append((values map { session.identity(it.key) }).makeString(", ", "", ""))
        sql.append(") ")

        sql.append("VALUES (")
        sql.append((values map { builder.registerArgument(it.value, it.key.columnType) }). makeString(", ", "", ""))

        sql.append(") ")
        log(sql)
        try {
            val autoincs: List<String> = table.columns.filter { it.columnType.let { it is IntegerColumnType && it.autoinc } } map {session.identity(it)}
            generatedKey = builder.executeUpdate(session, sql.toString(), autoincs)
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }
    }
}
