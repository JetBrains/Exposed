package kotlin.sql

import java.sql.Statement
import java.util.LinkedHashMap
import java.sql.PreparedStatement
import java.sql.Blob

class InsertSelectQuery(val table: Table, val selectQuery: Query, val isIgnore: Boolean = false) {
    var statement: Statement? = null

    fun get(column: Column<Int>): Int {
        //TODO: use column!!!
        val rs = (statement?:error("Statement is not executed")).getGeneratedKeys()!!;
        if (rs.next()) {
            return rs.getInt(1)
        } else {
            throw IllegalStateException("No key generated after statement: $statement")
        }
    }

    fun execute(session: Session) {
        val columns = table.columns.filter {
            val columntType = it.columnType as? IntegerColumnType
            columntType == null || !columntType.autoinc
        }.map { session.identity(it) }.makeString(", ", "(", ")")
        val ignore = if (isIgnore) " IGNORE " else ""
        var sql = "INSERT ${ignore}INTO ${session.identity(table)} $columns ${selectQuery.toSQL(QueryBuilder(false))}"
        session.logger.log(sql)
        try {
            statement = session.connection.createStatement()!!
            statement!!.executeUpdate(sql.toString(), Statement.RETURN_GENERATED_KEYS)
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }
    }
}
