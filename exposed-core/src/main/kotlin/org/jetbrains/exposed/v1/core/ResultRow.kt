package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.transactions.currentTransactionOrNull
import org.jetbrains.exposed.v1.core.vendors.withDialect

/** A row of data representing a single record retrieved from a database result set. */
class ResultRow(
    /** Mapping of the expressions stored on this row to their index positions. */
    val fieldIndex: Map<Expression<*>, Int>,
    private val data: Array<Any?> = arrayOfNulls<Any?>(fieldIndex.size)
) {
    @OptIn(InternalApi::class)
    private val database: DatabaseApi? = currentTransactionOrNull()?.db

    private val lookUpCache = ResultRowCache(fieldIndex)

    /**
     * Retrieves the value of a given expression on this row.
     *
     * @param expression expression to evaluate
     * @throws IllegalStateException if expression is not in record set or if result value is uninitialized
     *
     * @see [getOrNull] to get null in the cases an exception would be thrown
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(expression: Expression<T>): T {
        val column = expression as? Column<*>
        return when {
            column?.isEntityIdentifier() == true && column.table is CompositeIdTable -> {
                val resultID = CompositeID {
                    column.table.idColumns.forEach { column ->
                        it[column as Column<EntityID<Any>>] = getInternal(column, checkNullability = true).value
                    }
                }
                EntityID(resultID, column.table) as T
            }
            column?.isJsonBColumnForCasting() == true -> try {
                val castExpression = CastToJson(column, column.columnType)
                getInternal(castExpression, checkNullability = true) as T
            } catch (_: IllegalStateException) {
                // DAO may cache an entity after insert with only its column field values cached
                getInternal(expression, checkNullability = true)
            }
            else -> getInternal(expression, checkNullability = true)
        }
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

    @OptIn(InternalApi::class)
    private fun <T> getInternal(expression: Expression<T>, checkNullability: Boolean): T = lookUpCache.cached(expression) {
        val rawValue = getRaw(expression)

        if (checkNullability) {
            if (rawValue == null && expression is Column<*> && expression.dbDefaultValue != null && !expression.columnType.nullable) {
                exposedLogger.warn(
                    "Column ${currentTransaction().fullIdentity(expression)} is marked as not null, " +
                        "has default db value, but returns null. Possible have to re-read it from DB."
                )
            }
        }

        database?.dialect?.let {
            withDialect(it) {
                rawToColumnValue(rawValue, expression)
            }
        } ?: rawToColumnValue(rawValue, expression)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> rawToColumnValue(raw: T?, expression: Expression<T>): T {
        return when {
            raw == null -> null
            raw == NotInitializedValue -> error("$expression is not initialized yet")
            expression is ExpressionWithColumnTypeAlias<T> -> rawToColumnValue(raw, expression.delegate)
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
                    is Column<*> -> (exp.columnType as? EntityIDColumnType<*>)?.idColumn == expression
                    is IExpressionAlias<*> -> exp.delegate == expression
                    else -> false
                }
            }?.let { exp -> fieldIndex[exp] }
            ?: error("$expression is not in record set")
    }

    override fun toString(): String =
        fieldIndex.entries.joinToString { "${it.key}=${data[it.value]}" }

    internal object NotInitializedValue

    companion object {
        /** Creates a [ResultRow] storing all expressions in [fieldsIndex] with their values retrieved from a [RowApi]. */
        fun create(rs: RowApi, fieldsIndex: Map<Expression<*>, Int>): ResultRow {
            return ResultRow(fieldsIndex).apply {
                fieldsIndex.forEach { (field, index) ->
                    val columnType: IColumnType<out Any>? = (field as? ExpressionWithColumnType)?.columnType
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
                    setInternal(it, it.defaultValueOrNotInitialized())
                }
            }
    }

    private fun <T> Column<T>.defaultValueOrNotInitialized(): Any? {
        return when {
            defaultValueFun != null -> when {
                columnType is ColumnWithTransform<*, *> -> {
                    (columnType as ColumnWithTransform<Any, Any>).unwrapRecursive(defaultValueFun!!())
                }
                else -> defaultValueFun!!()
            }
            columnType.nullable -> null
            else -> NotInitializedValue
        }
    }

    /**
     * [ResultRowCache] caches converted values on reads, keyed by expression identity and column type.
     *
     * Design: a fixed-size array (one slot per fieldIndex position) covers the common case with zero
     * object allocations on cache hits. A lazy overflow HashMap handles the rare case where the same
     * field-index slot is observed through two different column-type views — e.g. a plain `Column<Int>`
     * and the wrapping `Column<EntityID<Int>>` that share the same table+name (and are therefore
     * `equals()`-equal) but carry different [IColumnType] instances and produce different converted values.
     *
     * The backing arrays are allocated eagerly with the row. [cacheType] is consulted on every cache hit
     * (not just conflicts) to validate the column-type view, so it must be tracked for every populated slot
     * and shares [cacheData]'s lifecycle. Keeping both as final fields gives the read hot path a tight,
     * branch-free shape (final-field reads + array access, no null checks).
     */
    private class ResultRowCache(private val fieldIndex: Map<Expression<*>, Int>) {
        companion object {
            // Sentinel distinguishing "slot not yet populated" from a legitimately cached null.
            private val UNCACHED = Any()
        }

        // Primary cache: indexed by fieldIndex position.
        private val cacheData = Array<Any?>(fieldIndex.size) { UNCACHED }

        // Column type stored alongside each primary-cache slot for type-conflict detection.
        private val cacheType = arrayOfNulls<IColumnType<*>>(fieldIndex.size)

        // Overflow: only allocated when a type-view conflict is encountered (rare in practice).
        private var overflow: HashMap<Pair<Expression<*>, IColumnType<*>?>, Any?>? = null

        /**
         * Returns the cached value for [expression], computing and storing it via [initializer] on a miss.
         *
         * Hot path (common columns): single array-bounds check + array read — no object allocation.
         * Overflow path (type-view conflict or expression absent from fieldIndex): falls back to a
         * lazily-created HashMap with a [Pair] key, preserving the original semantics.
         */
        fun <T> cached(expression: Expression<*>, initializer: () -> T): T {
            val colType = (expression as? Column<*>)?.columnType
            val index = fieldIndex[expression]

            if (index != null) {
                val current = cacheData[index]
                when {
                    current === UNCACHED -> {
                        // First access for this slot: populate the primary cache.
                        val value = initializer()
                        cacheData[index] = value
                        cacheType[index] = colType
                        return value
                    }
                    cacheType[index] == colType -> {
                        // Cache hit: same slot, same column-type view.
                        @Suppress("UNCHECKED_CAST")
                        return current as T
                    }
                    // else: type-view conflict — fall through to overflow.
                }
            }

            // Overflow path: expression not directly resolvable to a primary slot, or slot already
            // holds a value for a different column-type view of the same field index.
            val key = expression to colType
            val ovf = overflow ?: HashMap<Pair<Expression<*>, IColumnType<*>?>, Any?>().also { overflow = it }
            @Suppress("UNCHECKED_CAST")
            return ovf.getOrPut(key, initializer) as T
        }

        /**
         * Invalidates the cached value for [expression].
         *
         * This clears the primary slot for [expression]'s field index and removes the single overflow
         * entry keyed by exactly `(expression, expression.columnType)`. It does **not** invalidate
         * overflow entries belonging to *other* column-type views of the same field index. For example,
         * after `set(entityIdCol, …)` the raw-id view cached under `(rawIdCol, IntegerColumnType)` is
         * left untouched, so a subsequent `row[rawIdCol]` may return the pre-set value. This matches the
         * pre-cache per-key invalidation semantics — it is a known limitation, not a regression.
         */
        fun remove(expression: Expression<*>) {
            val index = fieldIndex[expression]
            if (index != null) {
                cacheData[index] = UNCACHED
            }
            overflow?.remove(expression to (expression as? Column<*>)?.columnType)
        }
    }
}
