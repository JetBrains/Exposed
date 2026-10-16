package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
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
        orderBy(column, if (order.isAscending) SortOrder.ASC else SortOrder.DESC)
    }
}
