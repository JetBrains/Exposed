package org.jetbrains.exposed.v1.core.dao.id

import org.jetbrains.exposed.v1.core.Column

/** Class representing a mapping of each composite primary key column to its stored identity value. */
class CompositeID private constructor() {
    internal val values: MutableMap<Column<*>, Any?> = HashMap()

    @Suppress("UNCHECKED_CAST")
    @JvmName("setWithEntityIdValue")
    operator fun <T : Any, ID : EntityID<T>> set(column: Column<ID>, value: T) {
        require(values.isEmpty() || values.keys.first().table == column.table) {
            "CompositeID key columns must all come from the same IdTable ${values.keys.first().table.tableName}"
        }
        values[column] = EntityID(value, column.table as IdTable<T>)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("setWithNullableEntityIdValue")
    operator fun <T : Any, ID : EntityID<T>> set(column: Column<ID?>, value: T?) {
        require(column.columnType.nullable || value != null) {
            "Trying to set null to not nullable column $column"
        }
        values[column] = value?.let { EntityID(value, column.table as IdTable<T>) }
    }

    @JvmName("setWithEntityID")
    operator fun <T : Any, ID : EntityID<T>> set(column: Column<ID>, value: ID) {
        require(values.isEmpty() || values.keys.first().table == column.table) {
            "CompositeID key columns must all come from the same IdTable ${values.keys.first().table.tableName}"
        }
        values[column] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(column: Column<T>): T = values[column] as T

    operator fun contains(column: Column<*>): Boolean = values.contains(column)

    override fun toString(): String = "CompositeID(${values.entries.joinToString { "${it.key.name}=${it.value}" }})"

    override fun hashCode(): Int = values.entries.fold(0) { acc, entry ->
        (acc * 31) + entry.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is EntityID<*>) return this == other._value
        if (other !is CompositeID) return false

        return values == other.values
    }

    companion object {
        operator fun invoke(body: (CompositeID) -> Unit): CompositeID {
            return CompositeID().apply(body).also {
                require(it.values.isNotEmpty()) {
                    "CompositeID must be initialized with at least one key column mapping"
                }
            }
        }
    }
}
