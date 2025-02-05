package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.Table

class MysqlDialectMetadata : DatabaseDialectMetadata() {
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
