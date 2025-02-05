package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.transactions.TransactionManager

class SQLiteDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean {
        return (TransactionManager.current() as JdbcTransaction).db.metadata { supportsLimitWithUpdateOrDelete() }
    }
}
