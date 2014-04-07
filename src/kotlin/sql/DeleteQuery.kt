package kotlin.sql

import java.sql.Connection

object DeleteQuery {
    fun where(session: Session, table: Table, op: Op<Boolean>): Int {
        val builder = QueryBuilder(true)
        val sql = StringBuilder("DELETE FROM ${session.identity(table)} WHERE ${op.toSQL(builder)}")
        session.logger.log(sql)
        return builder.executeUpdate(session, sql.toString())
    }

    fun all(session: Session, table: Table): Int {
        val sql = StringBuilder("DELETE FROM ${session.identity(table)}")
        session.logger.log(sql)
        return session.connection.createStatement()!!.executeUpdate(sql.toString())
    }
}
