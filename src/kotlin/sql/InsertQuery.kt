package kotlin.sql

import java.sql.Statement
import java.util.LinkedHashMap
import java.sql.PreparedStatement
import java.sql.Blob

class InsertQuery(val table: Table) {
    val values = LinkedHashMap<Column<*>, Any?>()
    var statement: Statement? = null

    fun <T> set(column: Column<T>, value: T) {
        if (values containsKey column) {
            throw RuntimeException("$column is already initialized")
        }

        values.put(column, if (value == null) null else {
            if (column.columnType is EnumerationColumnType<*>) (value as Enum<*>).ordinal() else value
        })
    }

    fun get(column: Column<Int>): Int {
        //TODO: use column!!!
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
        sql.append((values map { it.key.columnType.valueToString(it.value) }). makeString(", ", "", ""))

        sql.append(") ")
        log(sql)
        try {
            if (values.keySet() any { it.columnType is BlobColumnType }) {
                val autoincs = table.columns.filter { it.columnType is IntegerColumnType && it.columnType.autoinc } map {session.identity(it)}
                statement = session.connection.prepareStatement(sql.toString(), autoincs.toArray(array("sample")))!!
                var count = 0
                for ((key, value) in values) {
                    count++
                    if (key.columnType is BlobColumnType) {
                        (statement as PreparedStatement).setBlob(count, value as Blob)
                    }
                }
                (statement as PreparedStatement).executeUpdate()
            }
            else {
                statement = session.connection.createStatement()!!
                statement!!.executeUpdate(sql.toString(), Statement.RETURN_GENERATED_KEYS)
            }
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }
    }
}
