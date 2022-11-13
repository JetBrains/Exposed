package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.isAutoInc
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager

abstract class BaseBatchInsertStatement(
    table: Table,
    ignore: Boolean,
    protected val shouldReturnGeneratedValues: Boolean = true
) : InsertStatement<List<ResultRow>>(table, ignore) {
    override val isAlwaysBatch = true

    internal val data = ArrayList<MutableMap<Column<*>, Any?>>()

    private fun Column<*>.isDefaultable() = columnType.nullable || defaultValueFun != null

    override operator fun <S> set(column: Column<S>, value: S) {
        if (data.size > 1 && column !in data[data.size - 2] && !column.isDefaultable()) {
            val fullIdentity = TransactionManager.current().fullIdentity(column)
            throw BatchDataInconsistentException("Can't set $value for $fullIdentity because previous insertion can't be defaulted for that column.")
        }
        super.set(column, value)
    }

    fun addBatch() {
        if (data.isNotEmpty()) {
            validateLastBatch()
            data[data.size - 1] = LinkedHashMap(values)
            allColumnsInDataSet.addAll(values.keys)
            values.clear()
            hasBatchedValues = true
        }
        data.add(values)
        arguments = null
    }

    internal fun removeLastBatch() {
        data.removeAt(data.size - 1)
        allColumnsInDataSet.clear()
        data.flatMapTo(allColumnsInDataSet) { it.keys }
        values.clear()
        values.putAll(data.last())
        arguments = null
        hasBatchedValues = data.size > 0
    }

    internal open fun validateLastBatch() {
        val tr = TransactionManager.current()
        val cantBeDefaulted = (allColumnsInDataSet - values.keys).filterNot { it.isDefaultable() }
        if (cantBeDefaulted.isNotEmpty()) {
            val columnList = cantBeDefaulted.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException(
                "Can't add a new batch because columns: $columnList don't have client default values. DB defaults don't support in batch inserts"
            )
        }
        val requiredInTargets = (targets.flatMap { it.columns } - values.keys).filter {
            !it.isDefaultable() && !it.columnType.isAutoInc && it.dbDefaultValue == null && it.columnType !is EntityIDColumnType<*>
        }
        if (requiredInTargets.any()) {
            val columnList = requiredInTargets.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException(
                "Can't add a new batch because columns: $columnList don't have default values. DB defaults don't support in batch inserts"
            )
        }
    }

    private val allColumnsInDataSet = mutableSetOf<Column<*>>()
    private fun allColumnsInDataSet() = allColumnsInDataSet +
        (data.lastOrNull()?.keys ?: throw BatchDataInconsistentException("No data provided for inserting into ${table.tableName}"))

    override var arguments: List<List<Pair<Column<*>, Any?>>>? = null
        get() = field ?: run {
            val nullableColumns by lazy { allColumnsInDataSet().filter { it.columnType.nullable } }
            data.map { single ->
                val valuesAndDefaults = super.valuesAndDefaults(single) as MutableMap
                val nullableMap = (nullableColumns - valuesAndDefaults.keys).associateWith { null }
                valuesAndDefaults.putAll(nullableMap)
                valuesAndDefaults.toList().sortedBy { it.first }
            }.apply { field = this }
        }

    override fun valuesAndDefaults(values: Map<Column<*>, Any?>) = arguments!!.first().toMap()

    override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi {
        return if (!shouldReturnGeneratedValues) transaction.connection.prepareStatement(sql, false)
        else super.prepared(transaction, sql)
    }
}
