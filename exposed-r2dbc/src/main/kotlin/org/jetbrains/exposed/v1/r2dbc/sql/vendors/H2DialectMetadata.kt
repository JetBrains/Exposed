package org.jetbrains.exposed.v1.r2dbc.sql.vendors

import org.jetbrains.exposed.v1.sql.Index
import org.jetbrains.exposed.v1.sql.Table

open class H2DialectMetadata : DatabaseDialectMetadata() {
    override suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>> = super.existingIndices(*tables)
        .mapValues { entry ->
            entry.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_") }
        }
        .filterValues { it.isNotEmpty() }
}
