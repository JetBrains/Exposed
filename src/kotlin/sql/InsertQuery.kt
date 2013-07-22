package kotlin.sql

import java.sql.Connection
import java.sql.Statement

class InsertQuery(val statement: Statement) {
    fun get(column: Column<Int>): Int {
        val rs = statement.getGeneratedKeys()!!;
        if (rs.next()) {
            return rs.getInt(1)
        } else {
            throw IllegalStateException("No key generated after statement: $statement")
        }
    }
}