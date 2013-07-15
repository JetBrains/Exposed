package kotlin.sql

import java.sql.Connection

class DeleteQuery(val connection: Connection, val table: Table) {
    fun where(op: Op) {
        var sql = StringBuilder("DELETE FROM ${table.tableName} WHERE $op")
        println("SQL: " + sql)
        connection.createStatement()!!.executeUpdate(sql.toString())
    }
}