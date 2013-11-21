package kotlin.sql

import java.sql.Connection

class DeleteQuery(val session: Session, val table: Table) {
    fun where(op: Op<Boolean>) {
        val builder = QueryBuilder(true)
        val sql = StringBuilder("DELETE FROM ${session.identity(table)} WHERE ${op.toSQL(builder)}")
        log(sql)
        builder.executeUpdate(session, sql.toString())
    }

    fun all() {
        val sql = StringBuilder("DELETE FROM ${session.identity(table)}")
        log(sql)
        session.connection.createStatement()!!.executeUpdate(sql.toString())
    }
}
