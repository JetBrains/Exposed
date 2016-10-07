package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Table
import java.util.*

/**
 * @author max
 */

abstract class UpdateBuilder<out T>(type: StatementType, targets: List<Table>): Statement<T>(type, targets) {
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    operator fun <S> set(column: Column<S>, value: S?) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        if (!column.columnType.nullable && value == null) {
            error("Trying to set null to not nullable column $column")
        }
        values[column] = value
    }

    open fun <S> update(column: Column<S>, value: Expression<S>) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        values[column] = value
    }
}
