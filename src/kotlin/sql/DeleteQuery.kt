package kotlin.sql

import java.sql.Connection

object DeleteQuery {
    fun where(session: Session, table: Table, op: Op<Boolean>) {
        val builder = QueryBuilder(true)
        val sql = StringBuilder("DELETE FROM ${session.identity(table)} WHERE ${op.toSQL(builder)}")
        log(sql)
        builder.executeUpdate(session, sql.toString())
    }

    fun all(session: Session, table: Table, ) {
        val sql = StringBuilder("DELETE FROM ${session.identity(table)}")
        log(sql)
        session.connection.createStatement()!!.executeUpdate(sql.toString())
    }
}
