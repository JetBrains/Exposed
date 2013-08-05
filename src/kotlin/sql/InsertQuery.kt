package kotlin.sql

import java.sql.Connection
import java.sql.Statement
import java.util.HashMap
import java.util.LinkedHashMap

class InsertQuery(val table: Table) {
    val values = LinkedHashMap<Column<*>, Any>()
    var statement: Statement? = null

    fun <T> set(column: Column<T>, value: T) {
        if (values containsKey column) {
            throw RuntimeException("$column is already initialized")
        }

        values.put(column, if (column.columnType is EnumerationColumnType<*>) (value as Enum<*>).ordinal() else value)
    }

    fun get(column: Column<Int>): Int { //TODO: use column!!!
        val rs = (statement?:throw RuntimeException("Statement is not executed")).getGeneratedKeys()!!;
        if (rs.next()) {
            return rs.getInt(1)
        } else {
            throw IllegalStateException("No key generated after statement: $statement")
        }
    }

    fun execute(session: Session) {
        var sql = StringBuilder("INSERT INTO ${session.identity(table)}")

        sql.append(" (")
        sql.append((values map { session.identity(it.key) }).makeString(", ", "", ""))
        sql.append(") ")

        sql.append("VALUES (")
        sql.append((values map {
            when(it.key.columnType) {
                is StringColumnType -> "'${it.value}'"
                else -> "${it.value}"
            }
        }). makeString( ", ", "", ""))

        sql.append(") ")
        println("SQL: " + sql.toString())
        statement = session.connection.createStatement()!!
        statement!!.executeUpdate(sql.toString(), Statement.RETURN_GENERATED_KEYS)
    }
}
