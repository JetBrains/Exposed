package org.jetbrains.exposed.v1.r2dbc.vendors

import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.Table

/**
 * H2 dialect metadata implementation.
 */
open class H2DialectMetadata : DatabaseDialectMetadata() {
    override suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>> = super.existingIndices(*tables)
        .mapValues { entry ->
            entry.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_") }
        }
        .filterValues { it.isNotEmpty() }
}
