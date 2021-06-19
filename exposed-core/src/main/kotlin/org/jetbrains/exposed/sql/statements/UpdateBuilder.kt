@file:Suppress("internal")
package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*

/**
 * @author max
 */

abstract class UpdateBuilder<out T>(type: StatementType, targets: List<Table>) : Statement<T>(type, targets) {
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    open operator fun <S> set(column: Column<S>, value: S) {
        require(column !in values) { "$column is already initialized" }
        column.validateValueBeforeUpdate(value)
        values[column] = value
    }

    private fun Column<*>.validateValueBeforeUpdate(value: Any?) {
        require(columnType.nullable || value != null && !(value is LiteralOp<*> && value.value == null)) {
            "Can't set NULL into non-nullable column ${table.tableName}.$name, column type is $columnType"
        }
        columnType.validateValueBeforeUpdate(value)
    }

    @JvmName("setWithEntityIdExpression")
    operator fun <S : Comparable<S>> set(column: Column<out EntityID<S>?>, value: Expression<out S?>) {
        @Suppress("UNCHECKED_CAST")
        set(column as Column<Any?>, value as Any?)
    }

    @JvmName("setWithEntityIdValue")
    operator fun <S : Comparable<S>> set(column: Column<out EntityID<S>?>, value: S?) {
        @Suppress("UNCHECKED_CAST")
        set(column as Column<Any?>, value as Any?)
    }

    /**
     * Sets column value to null.
     * This method is helpful for "optional references" since compiler can't decide between
     * "null as T?" and "null as EntityID<T>?".
     */
    fun <T> setNull(column: Column<T?>) = set(column, null as T?)

    open operator fun <S> set(column: Column<S>, value: Expression<out S>) = update(column, value)

    open operator fun <S> set(column: CompositeColumn<S>, value: S) {
        column.getRealColumnsWithValues(value).forEach { (realColumn, itsValue) ->
            @Suppress("UNCHECKED_CAST")
            set(realColumn as Column<Any?>, itsValue)
        }
    }

    open fun <S> update(column: Column<S>, value: Expression<out S>) {
        @Suppress("UNCHECKED_CAST")
        set(column as Column<Any?>, value as Any?)
    }

    open fun <S> update(column: Column<S>, value: SqlExpressionBuilder.() -> Expression<out S>) {
        // Note: the implementation builds value before it verifies if the column is already initialized
        // however it makes the implementation easier and the optimization is not that important since
        // the exceptional case should be rare.
        set(column, SqlExpressionBuilder.value())
    }
}
