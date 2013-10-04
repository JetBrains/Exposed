package kotlin.sql

import java.sql.Connection

class DeleteQuery(val session: Session, val table: Table) {
    fun where(op: Op<Boolean>) {
        val sql = StringBuilder("DELETE FROM ${session.identity(table)} WHERE ${op.toSQL()}")
        log(sql)
        session.connection.createStatement()!!.executeUpdate(sql.toString())
    }

    fun all() {
        val sql = StringBuilder("DELETE FROM ${session.identity(table)}")
        log(sql)
        session.connection.createStatement()!!.executeUpdate(sql.toString())
    }
}
