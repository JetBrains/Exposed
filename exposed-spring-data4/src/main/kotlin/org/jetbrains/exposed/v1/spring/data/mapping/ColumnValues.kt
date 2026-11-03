package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.RedshiftDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.Query
import org.springframework.data.domain.Sort

internal fun Any?.unwrapEntityId(): Any? = if (this is EntityID<*>) value else this

@Suppress("UNCHECKED_CAST")
internal fun Column<*>.toDbValue(value: Any?): Any? {
    val idColumnType = columnType as? EntityIDColumnType<*> ?: return value
    if (value == null || value is EntityID<*>) return value
    return EntityID(value, idColumnType.idColumn.table as IdTable<Any>)
}

internal fun Query.orderBy(sort: Sort, metadata: EntityMetadata<*, *>): Query = apply {
    sort.forEach { order ->
        val column = requireNotNull(metadata.columnFor(order.property)) {
            "Unknown sort property '${order.property}' for table ${metadata.table.tableName}"
        }
        orderBy(column, order.toSortOrder())
    }
}

private fun Sort.Order.toSortOrder(): SortOrder = when (nullHandling) {
    Sort.NullHandling.NULLS_FIRST -> if (isAscending) SortOrder.ASC_NULLS_FIRST else SortOrder.DESC_NULLS_FIRST
    Sort.NullHandling.NULLS_LAST -> if (isAscending) SortOrder.ASC_NULLS_LAST else SortOrder.DESC_NULLS_LAST
    else -> if (isAscending) SortOrder.ASC else SortOrder.DESC
}

internal fun nullsSortHigh(): Boolean = when (val dialect = currentDialect) {
    is PostgreSQLDialect, is OracleDialect, is RedshiftDialect -> true
    is H2Dialect -> dialect.h2Mode == H2Dialect.H2CompatibilityMode.PostgreSQL || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
    else -> false
}
