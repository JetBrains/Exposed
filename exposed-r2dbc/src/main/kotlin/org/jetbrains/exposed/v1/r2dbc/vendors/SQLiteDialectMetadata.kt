package org.jetbrains.exposed.v1.r2dbc.vendors

import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

// TODO clean up SQLite - if not supported, why here & in R2dbcDatabase.registerDialect()???
open class SQLiteDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean {
        return TransactionManager.current().db.metadata { supportsLimitWithUpdateOrDelete() }
    }
}
