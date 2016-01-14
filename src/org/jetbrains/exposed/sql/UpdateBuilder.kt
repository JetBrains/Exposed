package org.jetbrains.exposed.sql

import java.util.*

/**
 * @author max
 */

open class UpdateBuilder {
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    operator fun <T, S: T> set(column: Column<T>, value: S?) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        if (!column.columnType.nullable && value == null) {
            error("Trying to set null to not nullable column $column")
        }
        values[column] = value
    }

    fun <T, S: T> update(column: Column<T>, value: Expression<S>) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        values[column] = value
    }
}
