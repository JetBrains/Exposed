package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

internal class BatchDataInconsistentException(message : String) : Exception(message)

open class BatchInsertStatement(table: Table, ignore: Boolean = false): InsertStatement<List<ResultRow>>(table, ignore) {

    override val flushCache: Boolean = false

    override val isAlwaysBatch = true

    override val generatedKey: List<ResultRow>?
        get() = resultedValues

    protected val data = ArrayList<MutableMap<Column<*>, Any?>>()

    private fun Column<*>.isDefaultable() = columnType.nullable || defaultValueFun != null

    override operator fun <S> set(column: Column<S>, value: S) {
        if (data.size > 1 && column !in data[data.size - 2] && !column.isDefaultable()) {
            throw BatchDataInconsistentException("Can't set $value for ${TransactionManager.current().fullIdentity(column)} because previous insertion can't be defaulted for that column.")
        }
        super.set(column, value)
    }

    fun addBatch() {
        if (data.isNotEmpty()) {
            validateLastBatch()
            data[data.size - 1] = LinkedHashMap(values)
            values.clear()
        }
        data.add(values)
        arguments = null
    }

    internal open fun validateLastBatch() {
        val cantBeDefaulted = (data.last().keys - values.keys).filterNot { it.isDefaultable() }
        if (cantBeDefaulted.isNotEmpty()) {
            val columnList = cantBeDefaulted.joinToString { TransactionManager.current().fullIdentity(it) }
            throw BatchDataInconsistentException("Can't add new batch because columns: $columnList don't have client default values. DB defaults don't support in batch inserts")
        }
        val requiredInTargets = (targets.flatMap { it.columns } - values.keys).filter { !it.isDefaultable() && !it.columnType.isAutoInc }
        if (requiredInTargets.any()) {
            throw BatchDataInconsistentException("Can't add new batch because columns: ${requiredInTargets.joinToString()} don't have client default values. DB defaults don't support in batch inserts")
        }
    }

    private fun allColumnsInDataSet() = data.fold(setOf<Column<*>>()) { columns, row ->
        columns + row.keys
    }

    override var arguments: List<List<Pair<Column<*>, Any?>>>? = null
        get() = field ?: run {
            val nullableColumns = allColumnsInDataSet().filter { it.columnType.nullable }
            data.map { single ->
                val valuesAndDefaults = super.valuesAndDefaults(single)
                (valuesAndDefaults + (nullableColumns - valuesAndDefaults.keys).associate { it to null }).toList().sortedBy { it.first }
            }.apply { field = this }
        }

    override fun valuesAndDefaults(values: Map<Column<*>, Any?>) = arguments!!.first().toMap()
}

open class SQLServerBatchInsertStatement(table: Table, ignore: Boolean = false) : BatchInsertStatement(table, ignore) {
    override val isAlwaysBatch: Boolean = false
    private val OUTPUT_ROW_LIMIT = 1000
    private val OUTPUT_PARAMS_LIMIT = 5000

    override fun validateLastBatch() {
        super.validateLastBatch()
        if (data.size > OUTPUT_ROW_LIMIT) {
            throw BatchDataInconsistentException("Too much rows in one batch. Exceed $OUTPUT_ROW_LIMIT limit")
        }
        val paramsToInsert = data.firstOrNull()?.size ?: 0
        if (paramsToInsert * (data.size + 1) > OUTPUT_PARAMS_LIMIT) {
            throw BatchDataInconsistentException("Too much parameters for batch with OUTPUT. Exceed $OUTPUT_PARAMS_LIMIT limit")
        }
    }

    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) ""
        else {
            val builder = QueryBuilder(true)
            val output = table.autoIncColumn?.let { " OUTPUT inserted.${transaction.identity(it)} AS GENERATED_KEYS" }.orEmpty()
            values.joinToString(prefix = "$output VALUES") {
                it.joinToString(prefix = "(", postfix = ")") { (col, value) ->
                    builder.registerArgument(col, value)
                }
            }
        }
        return transaction.db.dialect.functionProvider.insert(isIgnore, table, values.firstOrNull()?.map { it.first }.orEmpty(), sql, transaction)
    }

    override fun arguments() = listOfNotNull(super.arguments().flatten().takeIf { data.isNotEmpty() })

    override fun PreparedStatement.execInsertFunction(): Pair<Int, ResultSet?> {
        return arguments!!.size to executeQuery()
    }
}
