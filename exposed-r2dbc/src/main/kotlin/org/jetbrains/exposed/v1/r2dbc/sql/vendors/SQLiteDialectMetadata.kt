package org.jetbrains.exposed.v1.r2dbc.sql.vendors

import org.jetbrains.exposed.v1.r2dbc.sql.transactions.TransactionManager

open class SQLiteDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean {
        return TransactionManager.current().db.metadata { supportsLimitWithUpdateOrDelete() }
    }
}
