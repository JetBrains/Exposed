package kotlin.sql

import kotlin.dao.EntityCache

object DeleteQuery {
    fun where(transaction: Transaction, table: Table, op: Op<Boolean>, isIgnore: Boolean = false): Int {
        val ignore = if (isIgnore && transaction.db.vendor == DatabaseVendor.MySql) "IGNORE" else ""
        val builder = QueryBuilder(true)
        val sql = StringBuilder("DELETE $ignore FROM ${transaction.identity(table)} WHERE ${op.toSQL(builder)}")
        return builder.executeUpdate(transaction, sql.toString()).apply {
            EntityCache.getOrCreate(transaction).removeTablesReferrers(listOf(table))
        }
    }

    fun all(transaction: Transaction, table: Table): Int {
        transaction.flushCache()
        val sql = StringBuilder("DELETE FROM ${transaction.identity(table)}").toString()
        return transaction.exec(sql) {
            transaction.connection.createStatement()!!.executeUpdate(sql)
        }
    }
}
