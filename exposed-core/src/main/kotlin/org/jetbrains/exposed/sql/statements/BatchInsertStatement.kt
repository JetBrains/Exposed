package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet
import java.util.*

class BatchDataInconsistentException(message : String) : Exception(message)

open class BatchInsertStatement(table: Table, ignore: Boolean = false,
                                protected val shouldReturnGeneratedValues: Boolean = true): InsertStatement<List<ResultRow>>(table, ignore) {

    override val isAlwaysBatch = true

    internal val data = ArrayList<MutableMap<Column<*>, Any?>>()

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
            allColumnsInDataSet.addAll(values.keys)
            values.clear()
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
    }

    internal open fun validateLastBatch() {
        val tr = TransactionManager.current()
        val cantBeDefaulted = (allColumnsInDataSet - values.keys).filterNot { it.isDefaultable() }
        if (cantBeDefaulted.isNotEmpty()) {
            val columnList = cantBeDefaulted.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException("Can't add a new batch because columns: $columnList don't have client default values. DB defaults don't support in batch inserts")
        }
        val requiredInTargets = (targets.flatMap { it.columns } - values.keys).filter { !it.isDefaultable() && !it.columnType.isAutoInc && it.dbDefaultValue == null && it.columnType !is EntityIDColumnType<*> }
        if (requiredInTargets.any()) {
            val columnList = requiredInTargets.joinToString { tr.fullIdentity(it) }
            throw BatchDataInconsistentException("Can't add a new batch because columns: $columnList don't have default values. DB defaults don't support in batch inserts")
        }
    }

    private val allColumnsInDataSet = mutableSetOf<Column<*>>()
    private fun allColumnsInDataSet() = allColumnsInDataSet + (data.lastOrNull()?.keys ?: throw BatchDataInconsistentException("No data provided for inserting into ${table.tableName}"))

    override var arguments: List<List<Pair<Column<*>, Any?>>>? = null
        get() = field ?: run {
            val nullableColumns by lazy { allColumnsInDataSet().filter { it.columnType.nullable } }
            data.map { single ->
                val valuesAndDefaults = super.valuesAndDefaults(single)
                (valuesAndDefaults + (nullableColumns - valuesAndDefaults.keys).associateWith { null }).toList().sortedBy { it.first }
            }.apply { field = this }
        }

    override fun valuesAndDefaults(values: Map<Column<*>, Any?>) = arguments!!.first().toMap()

    override fun prepared(transaction: Transaction, sql: String): PreparedStatementApi {
        return if (!shouldReturnGeneratedValues)
            transaction.connection.prepareStatement(sql, false)
        else
            super.prepared(transaction, sql)
    }
}

open class SQLServerBatchInsertStatement(table: Table, ignore: Boolean = false, shouldReturnGeneratedValues: Boolean = true) : BatchInsertStatement(table, ignore, shouldReturnGeneratedValues) {
    override val isAlwaysBatch: Boolean = false
    private val OUTPUT_ROW_LIMIT = 1000

    override fun validateLastBatch() {
        super.validateLastBatch()
        if (data.size > OUTPUT_ROW_LIMIT) {
            throw BatchDataInconsistentException("Too much rows in one batch. Exceed $OUTPUT_ROW_LIMIT limit")
        }
    }

    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) ""
        else {
            val output = table.autoIncColumn?.let { " OUTPUT inserted.${transaction.identity(it)} AS GENERATED_KEYS" }?.takeIf { shouldReturnGeneratedValues }.orEmpty()
            QueryBuilder(true).apply {
                values.appendTo(prefix = "$output VALUES") {
                    it.appendTo(prefix = "(", postfix = ")") { (col, value) ->
                        registerArgument(col, value)
                    }
                }
            }.toString()
        }
        return transaction.db.dialect.functionProvider.insert(isIgnore, table, values.firstOrNull()?.map { it.first }.orEmpty(), sql, transaction)
    }

    override fun arguments() = listOfNotNull(super.arguments().flatten().takeIf { data.isNotEmpty() })

    override fun PreparedStatementApi.execInsertFunction(): Pair<Int, ResultSet?> {
        return arguments!!.size to executeQuery()
    }
}
