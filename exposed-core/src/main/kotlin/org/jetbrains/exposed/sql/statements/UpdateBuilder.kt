@file:Suppress("internal")
package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.util.*

/**
 * @author max
 */

abstract class UpdateBuilder<out T>(type: StatementType, targets: List<Table>): Statement<T>(type, targets) {
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    open operator fun <S> set(column: Column<S>, value: S) {
        when {
            values.containsKey(column) -> error("$column is already initialized")
            !column.columnType.nullable && value == null -> error("Trying to set null to not nullable column $column")
            else -> values[column] = value
        }
    }

    @JvmName("setWithEntityIdExpression")
    operator fun <S, ID:EntityID<S>, E: Expression<S>> set(column: Column<ID>, value: E) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    @JvmName("setWithEntityIdValue")
    operator fun <S:Comparable<S>, ID:EntityID<S>, E: S?> set(column: Column<ID>, value: E) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    open operator fun <T, S:T, E:Expression<S>> set(column: Column<T>, value: E) = update(column, value)

    open operator fun <S> set(column: CompositeColumn<S>, value: S) {
        column.getRealColumnsWithValues(value).forEach { (realColumn, itsValue) -> set(realColumn as Column<Any?>, itsValue) }
    }

    open fun <T, S:T?> update(column: Column<T>, value: Expression<S>) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    open fun <T, S:T?> update(column: Column<T>, value: SqlExpressionBuilder.() -> Expression<S>) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = SqlExpressionBuilder.value()
    }
}
