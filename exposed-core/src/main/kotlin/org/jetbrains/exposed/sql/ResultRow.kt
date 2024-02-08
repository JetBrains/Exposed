package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.withDialect
import java.sql.ResultSet

/** A row of data representing a single record retrieved from a database result set. */
class ResultRow(
    /** Mapping of the expressions stored on this row to their index positions. */
    val fieldIndex: Map<Expression<*>, Int>,
    private val data: Array<Any?> = arrayOfNulls<Any?>(fieldIndex.size)
) {
    private val database: Database? = TransactionManager.currentOrNull()?.db
    private val lookUpCache = HashMap<Expression<*>, Any?>()

    /**
     * Retrieves the value of a given expression on this row.
     *
     * @param expression expression to evaluate
     * @throws IllegalStateException if expression is not in record set or if result value is uninitialized
     *
     * @see [getOrNull] to get null in the cases an exception would be thrown
     */
    operator fun <T> get(expression: Expression<T>): T {
        val column = expression as? Column<*>
        return when {
            column?.columnType is EntityIDColumnType<*> && column.table is CompositeIdTable -> getIdComponents(column.table)
            else -> getInternal(expression, checkNullability = true)
        }
    }

    /**
     * Retrieves the value for each component column from the specified [table] `id` column and returns the
     * collective values as an [EntityID] value.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getIdComponents(table: CompositeIdTable): T {
        val resultMap = table.idColumns.associateWith { column ->
            getInternal(column, checkNullability = true)
        }
        return EntityID(CompositeID(resultMap), table) as T
    }

    /**
     * Sets the value of a given expression on this row.
     *
     * @param expression expression for which to set the value
     * @param value value to be set for the given expression
     */
    operator fun <T> set(expression: Expression<out T>, value: T) {
        setInternal(expression, value)
        lookUpCache.remove(expression)
    }

    private fun <T> setInternal(expression: Expression<out T>, value: T) {
        val index = getExpressionIndex(expression)
        data[index] = value
    }

    /** Whether the given [expression] has been initialized with a value on this row. */
    fun <T> hasValue(expression: Expression<T>): Boolean = fieldIndex[expression]?.let { data[it] != NotInitializedValue } ?: false

    /**
     * Retrieves the value of a given expression on this row.
     * Returns null in the cases an exception would be thrown in [get].
     *
     * @param expression expression to evaluate
     */
    fun <T> getOrNull(expression: Expression<T>): T? = if (hasValue(expression)) getInternal(expression, checkNullability = false) else null

    private fun <T> getInternal(expression: Expression<T>, checkNullability: Boolean): T {
        if (expression in lookUpCache) return lookUpCache[expression] as T

        val d = getRaw(expression)

        if (checkNullability) {
            if (d == null && expression is Column<*> && expression.dbDefaultValue != null && !expression.columnType.nullable) {
                exposedLogger.warn(
                    "Column ${TransactionManager.current().fullIdentity(expression)} is marked as not null, " +
                        "has default db value, but returns null. Possible have to re-read it from DB."
                )
            }
        }

        val result = database?.dialect?.let {
            withDialect(it) {
                rawToColumnValue(d, expression)
            }
        } ?: rawToColumnValue(d, expression)
        lookUpCache[expression] = result
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> rawToColumnValue(raw: T?, expression: Expression<T>): T {
        return when {
            raw == null -> null
            raw == NotInitializedValue -> error("$expression is not initialized yet")
            expression is ExpressionAlias<T> -> rawToColumnValue(raw, expression.delegate)
            expression is ExpressionWithColumnType<T> -> expression.columnType.valueFromDB(raw)
            expression is Op.OpBoolean -> BooleanColumnType.INSTANCE.valueFromDB(raw)
            else -> raw
        } as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRaw(expression: Expression<T>): T? {
        if (expression is CompositeColumn<T>) {
            val rawParts = expression.getRealColumns().associateWith { getRaw(it) }
            return expression.restoreValueFromParts(rawParts)
        }

        val index = getExpressionIndex(expression)
        return data[index] as T?
    }

    /**
     * Retrieves the index of a given expression in the [fieldIndex] map.
     *
     * @param expression expression for which to get the index
     * @throws IllegalStateException if expression is not in record set
     */
    private fun <T> getExpressionIndex(expression: Expression<T>): Int {
        return fieldIndex[expression]
            ?: fieldIndex.keys.firstOrNull { exp ->
                when (exp) {
                    // exp is Column<*> && exp.table is Alias<*> -> exp.table.delegate == c
                    is Column<*> -> (exp.columnType as? EntityIDColumnType<*>)?.idColumn == expression
                    is ExpressionAlias<*> -> exp.delegate == expression
                    else -> false
                }
            }?.let { exp -> fieldIndex[exp] }
            ?: error("$expression is not in record set")
    }

    override fun toString(): String =
        fieldIndex.entries.joinToString { "${it.key}=${data[it.value]}" }

    internal object NotInitializedValue

    companion object {
        /** Creates a [ResultRow] storing all expressions in [fieldsIndex] with their values retrieved from a [ResultSet]. */
        fun create(rs: ResultSet, fieldsIndex: Map<Expression<*>, Int>): ResultRow {
            return ResultRow(fieldsIndex).apply {
                fieldsIndex.forEach { (field, index) ->
                    val columnType = (field as? ExpressionWithColumnType)?.columnType
                    val value = if (columnType != null) {
                        columnType.readObject(rs, index + 1)
                    } else {
                        rs.getObject(index + 1)
                    }
                    data[index] = value
                }
            }
        }

        /** Creates a [ResultRow] using the expressions and values provided by [data]. */
        fun createAndFillValues(data: Map<Expression<*>, Any?>): ResultRow {
            val fieldIndex = HashMap<Expression<*>, Int>(data.size)
            val values = arrayOfNulls<Any?>(data.size)
            data.entries.forEachIndexed { i, columnAndValue ->
                val (column, value) = columnAndValue
                fieldIndex[column] = i
                values[i] = value
            }
            return ResultRow(fieldIndex, values)
        }

        /** Creates a [ResultRow] storing [columns] with their default or nullable values. */
        fun createAndFillDefaults(columns: List<Column<*>>): ResultRow =
            ResultRow(columns.withIndex().associate { it.value to it.index }).apply {
                columns.forEach {
                    val value = when {
                        it.defaultValueFun != null -> it.defaultValueFun!!()
                        it.columnType.nullable -> null
                        else -> NotInitializedValue
                    }
                    setInternal(it, value)
                }
            }
    }
}
