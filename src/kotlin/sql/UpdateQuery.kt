package kotlin.sql

import java.sql.Connection

class UpdateQuery(val session: Session, val pairs: Array<Pair<Column<*>, *>>) {
    fun where(op: Op) {
        if (pairs.size > 0) {
            val table = pairs[0].component1().table
            var sql = StringBuilder("UPDATE ${session.identity(table)}")
            var c = 0;
            sql.append(" ")
            for (pair in pairs) {
                sql.append("SET ").append(session.identity(pair.component1())).append(" = ")
                when (pair.component1().columnType) {
                    ColumnType.STRING -> sql.append("'").append(pair.component2()).append("'")
                    else -> sql.append(pair.component2())
                }
                c++
                if (c < pairs.size) {
                    sql.append(", ")
                }
            }
            sql.append(" WHERE " + op.toSQL())
            println("SQL: " + sql)
            session.connection.createStatement()!!.executeUpdate(sql.toString())
        }
    }
}