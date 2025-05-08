package org.jetbrains.exposed.v1.sql.vendors

import org.jetbrains.exposed.v1.sql.transactions.TransactionManager

open class SQLiteDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean {
        return TransactionManager.current().db.metadata { supportsLimitWithUpdateOrDelete() }
    }
}
