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

/** Converts a value read from a row to the value of the entity property, i.e. `EntityID<Long>` to `Long`. */
internal fun Any?.unwrapEntityId(): Any? = if (this is EntityID<*>) value else this

/** Converts a property value to the value of this column, i.e. `Long` to `EntityID<Long>` for id and reference columns. */
@Suppress("UNCHECKED_CAST")
internal fun Column<*>.toDbValue(value: Any?): Any? {
    val idColumnType = columnType as? EntityIDColumnType<*> ?: return value
    if (value == null || value is EntityID<*>) return value
    return EntityID(value, idColumnType.idColumn.table as IdTable<Any>)
}

/** Adds an `ORDER BY` for every property of [sort], failing for properties that have no column. */
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

/**
 * Whether the current database sorts `NULL` as larger than every other value when no `NULLS FIRST` / `NULLS LAST` is given
 * (so last in ascending and first in descending order). Needs an active transaction.
 */
internal fun nullsSortHigh(): Boolean = when (val dialect = currentDialect) {
    is PostgreSQLDialect, is OracleDialect, is RedshiftDialect -> true
    is H2Dialect -> dialect.h2Mode == H2Dialect.H2CompatibilityMode.PostgreSQL || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle
    else -> false
}
