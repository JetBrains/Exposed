package org.jetbrains.exposed.v1.r2dbc.sql.vendors

import org.jetbrains.exposed.v1.sql.InternalApi
import org.jetbrains.exposed.v1.sql.Table

open class MysqlDialectMetadata : DatabaseDialectMetadata() {
    @OptIn(InternalApi::class)
    override fun String.metadataMatchesTable(schema: String, table: Table): Boolean {
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
