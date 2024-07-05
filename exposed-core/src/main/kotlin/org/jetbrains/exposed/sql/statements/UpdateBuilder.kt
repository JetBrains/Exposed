@file:Suppress("internal", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import kotlin.internal.LowPriorityInOverloadResolution

/**
 * Represents the underlying mapping of columns scheduled for change along with their new values.
 */
abstract class UpdateBuilder<out T>(type: StatementType, targets: List<Table>) : Statement<T>(type, targets) {
    /** The mapping of columns scheduled for change with their new values. */
    protected val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    open operator fun contains(column: Column<*>): Boolean = values.contains(column)

    /** Whether the underlying mapping has at least one stored value that is a batched statement. */
    protected var hasBatchedValues: Boolean = false

    private fun checkThatExpressionWasNotSetInPreviousBatch(column: Column<*>) {
        require(!(values.containsKey(column) && hasBatchedValues)) { "$column is already initialized in a batch" }
    }

    @LowPriorityInOverloadResolution
    open operator fun <S> set(column: Column<S>, value: S) {
        require(column.columnType.nullable || (value != null && value !is Op.NULL)) {
            "Trying to set null to not nullable column $column"
        }

        if (column.columnType.isEntityIdentifier() && (value as EntityID<*>).value is CompositeID) {
            (value.value as CompositeID).setComponentValues()
        } else {
            column.columnType.validateValueBeforeUpdate(value)
            values[column] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("setWithEntityIdValue")
    operator fun <S : Comparable<S>> set(column: Column<EntityID<S>>, value: S) {
        if (value is CompositeID) {
            value.setComponentValues()
        } else {
            val entityId: EntityID<S> = EntityID(value, (column.foreignKey?.targetTable ?: column.table) as IdTable<S>)
            column.columnType.validateValueBeforeUpdate(entityId)
            values[column] = entityId
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("setWithNullableEntityIdValue")
    operator fun <S : Comparable<S>> set(column: Column<EntityID<S>?>, value: S?) {
        require(column.columnType.nullable || value != null) {
            "Trying to set null to not nullable column $column"
        }
        if (value is CompositeID) {
            value.setComponentValues()
        } else {
            val entityId: EntityID<S>? = value?.let { EntityID(it, (column.foreignKey?.targetTable ?: column.table) as IdTable<S>) }
            column.columnType.validateValueBeforeUpdate(entityId)
            values[column] = entityId
        }
    }

    @JvmName("setWithEntityIdExpression")
    operator fun <S : Any?, ID : EntityID<S>, E : Expression<S>> set(column: Column<ID>, value: E) {
        require(column.columnType.nullable || value !is Op.NULL) {
            "Trying to set null to not nullable column $column"
        }
        checkThatExpressionWasNotSetInPreviousBatch(column)
        values[column] = value
    }

    open operator fun <T, S : T?, E : Expression<S>> set(column: Column<T>, value: E) = update(column, value)

    open operator fun <S> set(column: Column<S>, value: Query) = update(column, wrapAsExpression(value))

    open operator fun <S> set(column: CompositeColumn<S>, value: S) {
        column.getRealColumnsWithValues(value).forEach { (realColumn, itsValue) ->
            set(realColumn as Column<Any?>, itsValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun CompositeID.setComponentValues() {
        this.values.forEach { (idColumn, idValue) ->
            set(idColumn as Column<Any?>, idValue)
        }
    }

    /**
     * Updates the mapping of the specified [column] with the specified [value] if [column] has not been previously
     * set up for a change and if [value] is of a valid type.
     **/
    open fun <T, S : T?> update(column: Column<T>, value: Expression<S>) {
        checkThatExpressionWasNotSetInPreviousBatch(column)
        values[column] = value
    }

    /** Updates the mapping of the specified [column] with the value of the provided expression. */
    open fun <T, S : T?> update(column: Column<T>, value: SqlExpressionBuilder.() -> Expression<S>) {
        update(column, SqlExpressionBuilder.value())
    }
}
