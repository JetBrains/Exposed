package kotlin.sql

object DeleteQuery {
    fun where(session: Session, table: Table, op: Op<Boolean>, isIgnore: Boolean = false): Int {
        val ignore = if (isIgnore && Session.get().vendor == DatabaseVendor.MySql) "IGNORE" else ""
        val builder = QueryBuilder(true)
        val sql = StringBuilder("DELETE $ignore FROM ${session.identity(table)} WHERE ${op.toSQL(builder)}")
        return builder.executeUpdate(session, sql.toString())
    }

    fun all(session: Session, table: Table): Int {
        session.flushCache()
        val sql = StringBuilder("DELETE FROM ${session.identity(table)}").toString()
        return session.exec(sql) {
            session.connection.createStatement()!!.executeUpdate(sql)
        }
    }
}
