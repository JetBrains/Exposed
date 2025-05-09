package org.jetbrains.exposed.v1.jdbc.vendors

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

open class SQLiteDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean {
        return TransactionManager.current().db.metadata { supportsLimitWithUpdateOrDelete() }
    }
}
