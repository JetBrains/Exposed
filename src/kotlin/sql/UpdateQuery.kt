package kotlin.sql

import java.sql.Connection

class UpdateQuery(val connection: Connection, val pairs: Array<Pair<Column<*>, *>>) {
    fun where(op: Op) {
        if (pairs.size > 0) {
            val table = pairs[0].component1().table
            var sql = StringBuilder("UPDATE ${table.tableName}")
            var c = 0;
            sql.append(" ")
            for (pair in pairs) {
                sql.append("SET ").append(pair.component1().name).append(" = ")
                when (pair.component1().columnType) {
                    ColumnType.STRING -> sql.append("'").append(pair.component2()).append("'")
                    else -> sql.append(pair.component2())
                }
                c++
                if (c < pairs.size) {
                    sql.append(", ")
                }
            }
            sql.append(" WHERE " + op.toString())
            println("SQL: " + sql)
            connection.createStatement()!!.executeUpdate(sql.toString())
        }
    }
}