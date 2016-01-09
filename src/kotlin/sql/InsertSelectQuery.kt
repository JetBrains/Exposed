package kotlin.sql

import java.sql.Statement

class InsertSelectQuery(val table: Table, val selectQuery: Query, val isIgnore: Boolean = false, val isReplace: Boolean = false) {
    var statement: Statement? = null

    operator fun get(column: Column<Int>): Int {
        //TODO: use column!!!
        val rs = (statement?:error("Statement is not executed")).generatedKeys!!;
        if (rs.next()) {
            return rs.getInt(1)
        } else {
            throw IllegalStateException("No key generated after statement: $statement")
        }
    }

    fun execute(transaction: Transaction) {
        val columns = table.columns.filter { !it.columnType.autoinc }.map { transaction.identity(it) }.joinToString(", ", "(", ")")
        val ignore = if (isIgnore && transaction.db.vendor != DatabaseVendor.H2) " IGNORE " else ""
        val insert = if (!isReplace) "INSERT" else "REPLACE"
        var sql = "$insert ${ignore}INTO ${transaction.identity(table)} $columns ${selectQuery.toSQL(QueryBuilder(false))}"

        transaction.exec(sql) {
            transaction.flushCache()
            try {
                statement = transaction.connection.createStatement()!!
                statement!!.executeUpdate(sql.toString(), Statement.RETURN_GENERATED_KEYS)
            } catch (e: Exception) {
                println("BAD SQL: $sql")
                throw e
            }
        }
    }
}
