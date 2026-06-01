package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isAutoInc
import org.jetbrains.exposed.v1.core.transactions.currentTransaction

/** An exception thrown when the provided data cannot be validated or processed to prepare a batch statement. */
class BatchDataInconsistentException(message: String) : Exception(message)

/**
 * Represents the SQL statement that batch inserts new rows into a table.
 *
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
 * should be returned. See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
open class BatchInsertStatement(
    table: Table,
    ignore: Boolean = false,
    val shouldReturnGeneratedValues: Boolean = true
) : InsertStatement<List<ResultRow>>(table, ignore) {

    // Sentinel stored in FlatRowMap.storage slots that were not explicitly set by the user.
    // Distinct from null so we can differentiate "set to null" from "not set".
    private val ABSENT: Any = object { override fun toString() = "ABSENT" }

    // Shared column registry — populated on first committed batch and grown if new columns appear later.
    private val batchColumns = ArrayList<Column<*>>()
    private val columnIndexMap = LinkedHashMap<Column<*>, Int>()

    /**
     * A [MutableMap] backed by a flat [Array] for cache-friendly per-row value storage.
     *
     * All instances within a single [BatchInsertStatement] share the same [columnIndexMap] and [batchColumns]
     * from the outer class. The [storage] array is the only per-row allocation (plus this object itself),
     * eliminating the per-entry [Map.Entry] allocations that [LinkedHashMap] would require.
     *
     * Columns that were not explicitly set for this row hold the private [ABSENT] sentinel so that
     * [containsKey] correctly returns `false` for them, preserving the same semantics as a map that
     * simply does not contain that key.
     */
    private inner class FlatRowMap(sourceValues: Map<Column<*>, Any?>) : AbstractMutableMap<Column<*>, Any?>() {

        val storage: Array<Any?>

        init {
            // Register any columns not yet tracked (first batch sets them all; later batches
            // may add new nullable columns, which is supported but rare).
            for (col in sourceValues.keys) {
                if (col !in columnIndexMap) {
                    columnIndexMap[col] = batchColumns.size
                    batchColumns.add(col)
                }
            }
            storage = Array(batchColumns.size) { i ->
                val col = batchColumns[i]
                if (sourceValues.containsKey(col)) sourceValues[col] else ABSENT
            }
        }

        // Returns null for columns not in this row (idx out of range) or marked ABSENT.
        override fun get(key: Column<*>): Any? {
            val idx = columnIndexMap[key] ?: return null
            if (idx >= storage.size) return null
            val v = storage[idx]
            return if (v === ABSENT) null else v
        }

        // A column "contains" a value only when the user explicitly set it (not ABSENT).
        override fun containsKey(key: Column<*>): Boolean {
            val idx = columnIndexMap[key] ?: return false
            if (idx >= storage.size) return false
            return storage[idx] !== ABSENT
        }

        override fun put(key: Column<*>, value: Any?): Any? {
            val idx = columnIndexMap[key] ?: return null
            if (idx >= storage.size) return null
            val old = storage[idx]
            storage[idx] = value
            return if (old === ABSENT) null else old
        }

        override val size: Int
            get() = storage.count { it !== ABSENT }

        // Keys are the columns that were explicitly set (non-ABSENT) in this row.
        override val keys: MutableSet<Column<*>>
            get() {
                val result = LinkedHashSet<Column<*>>(size)
                batchColumns.forEachIndexed { i, col ->
                    if (i < storage.size && storage[i] !== ABSENT) result.add(col)
                }
                return result
            }

        override val entries: MutableSet<MutableMap.MutableEntry<Column<*>, Any?>>
            get() = object : AbstractMutableSet<MutableMap.MutableEntry<Column<*>, Any?>>() {
                override val size get() = this@FlatRowMap.size

                override fun add(element: MutableMap.MutableEntry<Column<*>, Any?>) =
                    throw UnsupportedOperationException()

                override fun iterator() = object : MutableIterator<MutableMap.MutableEntry<Column<*>, Any?>> {
                    private var i = advanceTo(0)

                    private fun advanceTo(start: Int): Int {
                        var idx = start
                        while (idx < storage.size && storage[idx] === ABSENT) idx++
                        return idx
                    }

                    override fun hasNext() = i < storage.size

                    override fun next(): MutableMap.MutableEntry<Column<*>, Any?> {
                        val colIdx = i
                        val col = batchColumns[colIdx]
                        i = advanceTo(i + 1)
                        return object : MutableMap.MutableEntry<Column<*>, Any?> {
                            override val key = col
                            override val value: Any?
                                get() = storage[colIdx].let { if (it === ABSENT) null else it }

                            override fun setValue(newValue: Any?): Any? {
                                val old = storage[colIdx]
                                storage[colIdx] = newValue
                                return if (old === ABSENT) null else old
                            }
                        }
                    }

                    override fun remove() = throw UnsupportedOperationException()
                }
            }
    }

    /** @suppress */
    @InternalApi
    val data = ArrayList<MutableMap<Column<*>, Any?>>()

    private fun Column<*>.isDefaultable() = columnType.nullable || defaultValueFun != null || isDatabaseGenerated

    override operator fun <S> set(column: Column<S>, value: S) {
        @OptIn(InternalApi::class)
        if (data.size > 1 && column !in data[data.size - 2] && !column.isDefaultable()) {
            val fullIdentity = currentTransaction().fullIdentity(column)
            throw BatchDataInconsistentException("Can't set $value for $fullIdentity because previous insertion can't be defaulted for that column.")
        }
        super.set(column, value)
    }

    /**
     * Adds the most recent batch to the current list of insert statements.
     *
     * This function uses the mapping of columns scheduled for change with their new values, which is
     * provided by the implementing `BatchInsertStatement` instance.
     */
    fun addBatch() {
        @OptIn(InternalApi::class)
        if (data.isNotEmpty()) {
            validateLastBatch()
            data[data.size - 1] = FlatRowMap(values)
            allColumnsInDataSet.addAll(values.keys)
            values.clear()
            hasBatchedValues = true
        }
        @OptIn(InternalApi::class)
        data.add(values)
        arguments = null
    }

    @OptIn(InternalApi::class)
    fun removeLastBatch() {
        data.removeAt(data.size - 1)
        allColumnsInDataSet.clear()
        data.flatMapTo(allColumnsInDataSet) { it.keys }
        values.clear()
        values.putAll(data.last())
        arguments = null
        hasBatchedValues = data.isNotEmpty()
    }

    /** @suppress */
    @InternalApi
    open fun validateLastBatch() {
        val tr = currentTransaction()
        val cantBeDefaulted = (allColumnsInDataSet - values.keys).filterNot { it.isDefaultable() }
        if (cantBeDefaulted.isNotEmpty()) {
            val columnList = cantBeDefaulted.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException(
                "Can't add a new batch because columns: $columnList don't have client default values. DB defaults are not supported in batch inserts"
            )
        }
        val requiredInTargets = (targets.flatMap { it.columns } - values.keys).filter {
            !it.isDefaultable() && !it.columnType.isAutoInc && it.dbDefaultValue == null && it.columnType !is EntityIDColumnType<*>
        }
        if (requiredInTargets.any()) {
            val columnList = requiredInTargets.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException(
                "Can't add a new batch because columns: $columnList don't have default values. DB defaults are not supported in batch inserts"
            )
        }
    }

    private val allColumnsInDataSet = mutableSetOf<Column<*>>()

    @OptIn(InternalApi::class)
    private fun allColumnsInDataSet() = allColumnsInDataSet +
        (data.lastOrNull()?.keys ?: throw BatchDataInconsistentException("No data provided for inserting into ${table.tableName}"))

    /**
     * Cached argument rows for all currently registered batches.
     *
     * `null` indicates that the arguments have not yet been materialized, or that the cache has been invalidated after
     * the statement was modified. Reading this property materializes the arguments from the current batch data.
     */
    override var arguments: List<List<Pair<Column<*>, Any?>>>? = null
        get() = field ?: run {
            val columnsToInsert = (allColumnsInDataSet() + clientDefaultColumns()).toSet()
            @OptIn(InternalApi::class)
            data
                .map { valuesAndClientDefaults(it) as MutableMap }
                .map { values ->
                    columnsToInsert.map { column ->
                        column to when {
                            values.contains(column) -> values[column]
                            column.dbDefaultValue != null || column.isDatabaseGenerated -> DefaultValueMarker
                            else -> {
                                require(column.columnType.nullable) {
                                    "The value for the column ${column.name} was not provided. " +
                                        "The value for non-nullable column without defaults must be specified."
                                }
                                null
                            }
                        }
                    }
                }.apply { field = this }
        }
}
