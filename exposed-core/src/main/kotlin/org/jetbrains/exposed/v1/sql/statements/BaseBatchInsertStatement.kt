package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.EntityIDColumnType
import org.jetbrains.exposed.v1.sql.InternalApi
import org.jetbrains.exposed.v1.sql.ResultRow
import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.isAutoInc
import org.jetbrains.exposed.v1.sql.transactions.CoreTransactionManager

/**
 * Base class representing the SQL statement that batch inserts new rows into a table.
 *
 * @param shouldReturnGeneratedValues Specifies whether newly generated values (for example, auto-incremented IDs)
 * should be returned. See [Batch Insert](https://github.com/JetBrains/Exposed/wiki/DSL#batch-insert) for more details.
 */
@Suppress("ForbiddenComment")
// TODO: Merge BatchInsertStatement with BaseBatchInsertStatement
abstract class BaseBatchInsertStatement(
    table: Table,
    ignore: Boolean,
    val shouldReturnGeneratedValues: Boolean = true
) : InsertStatement<List<ResultRow>>(table, ignore) {
    @InternalApi
    val data = ArrayList<MutableMap<Column<*>, Any?>>()

    private fun Column<*>.isDefaultable() = columnType.nullable || defaultValueFun != null || isDatabaseGenerated

    override operator fun <S> set(column: Column<S>, value: S) {
        @OptIn(InternalApi::class)
        if (data.size > 1 && column !in data[data.size - 2] && !column.isDefaultable()) {
            val fullIdentity = CoreTransactionManager.currentTransaction().fullIdentity(column)
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
            data[data.size - 1] = LinkedHashMap(values)
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
        hasBatchedValues = data.size > 0
    }

    @InternalApi
    open fun validateLastBatch() {
        val tr = CoreTransactionManager.currentTransaction()
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
