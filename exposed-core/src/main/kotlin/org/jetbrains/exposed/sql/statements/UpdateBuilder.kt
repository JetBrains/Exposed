@file:Suppress("internal")

package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.CompositeColumn
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.wrapAsExpression

/**
 * @author max
 */

abstract class UpdateBuilder<out T>(type: StatementType, targets: List<Table>) : Statement<T>(type, targets) {
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    open operator fun contains(column: Column<*>): Boolean = values.contains(column)

    protected var hasBatchedValues: Boolean = false
    private fun checkThatExpressionWasNotSetInPreviousBatch(column: Column<*>) {
        require(!(values.containsKey(column) && hasBatchedValues)) { "$column is already initialized in a batch" }
    }

    open operator fun <S> set(column: Column<S>, value: S) {
        require(column.columnType.nullable || (value != null && value != Op.NULL)) {
            "Trying to set null to not nullable column $column"
        }

        column.columnType.validateValueBeforeUpdate(value)
        values[column] = value
    }

    @JvmName("setWithEntityIdValue")
    operator fun <S : Comparable<S>, E : S, ID : EntityID<E>?> set(column: Column<ID>, value: S) {
        column.columnType.validateValueBeforeUpdate(value)
        values[column] = value
    }

    @JvmName("setWithEntityIdExpression")
    operator fun <S : Any?, ID : EntityID<S>, E : Expression<S>> set(column: Column<ID>, value: E) {
        require(column.columnType.nullable || value != Op.NULL) {
            "Trying to set null to not nullable column $column"
        }
        checkThatExpressionWasNotSetInPreviousBatch(column)
        column.columnType.validateValueBeforeUpdate(value)
        values[column] = value
    }

    open operator fun <T, S : T, E : Expression<S>> set(column: Column<T>, value: E) = update(column, value)

    open operator fun <S> set(column: Column<S>, value: Query) = update(column, wrapAsExpression(value))

    open operator fun <S : Any> set(column: CompositeColumn<S>, value: S) {
        column.getRealColumnsWithValues(value).forEach { (realColumn, itsValue) -> set(realColumn as Column<Any?>, itsValue) }
    }

    open fun <T, S : T?> update(column: Column<T>, value: Expression<S>) {
        checkThatExpressionWasNotSetInPreviousBatch(column)
        column.columnType.validateValueBeforeUpdate(value)
        values[column] = value
    }

    open fun <T, S : T?> update(column: Column<T>, value: SqlExpressionBuilder.() -> Expression<S>) {
        update(column, SqlExpressionBuilder.value())
    }
}
