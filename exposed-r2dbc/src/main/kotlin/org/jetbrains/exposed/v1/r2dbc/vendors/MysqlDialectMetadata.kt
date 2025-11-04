package org.jetbrains.exposed.v1.r2dbc.vendors

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.inProperCase

/**
 * MySQL dialect metadata implementation.
 */
open class MysqlDialectMetadata : DatabaseDialectMetadata() {
    override fun String.metadataMatchesTable(schema: String, table: Table): Boolean {
        @OptIn(InternalApi::class)
        return when {
            schema.isEmpty() -> this == table.nameInDatabaseCaseUnquoted()
            else -> {
                val sanitizedTableName = table.tableNameWithoutScheme.replace("`", "")
                val nameInDb = "$schema.$sanitizedTableName".inProperCase()
                this == nameInDb
            }
        }
    }
}
