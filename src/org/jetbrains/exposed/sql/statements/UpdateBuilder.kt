package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.util.*

/**
 * @author max
 */

abstract class UpdateBuilder<T>(type: StatementType, targets: List<Table>): Statement<T>(type, targets) {
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

    open fun <T, S: T> update(column: Column<T>, value: Expression<S>) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        values[column] = value
    }
}
