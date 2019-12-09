package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.util.*

open class BatchUpdateStatement(val table: IdTable<*>): UpdateStatement(table, null) {
    val data = ArrayList<Pair<EntityID<*>, Map<Column<*>, Any?>>>()

    override val firstDataSet: List<Pair<Column<*>, Any?>> get() = data.first().second.toList()

    fun addBatch(id: EntityID<*>) {
        val lastBatch = data.lastOrNull()
        val different by lazy {
            val set1 = firstDataSet.map { it.first }.toSet()
            val set2 = lastBatch!!.second.keys
            (set1 - set2) + (set2 - set1)
        }

        if (data.size > 1 && different.isNotEmpty()) {
            throw BatchDataInconsistentException("Some values missing for batch update. Different columns: $different")
        }

        if (data.isNotEmpty()) {
            data[data.size - 1] = lastBatch!!.copy(second = values.toMap())
            values.clear()
        }
        data.add(id to values)
    }

    override fun <T, S : T?> update(column: Column<T>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction): String =
            "${super.prepareSQL(transaction)} WHERE ${transaction.identity(table.id)} = ?"

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int = if (data.size == 1) executeUpdate() else executeBatch().sum()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = data.map { (id, row) ->
        firstDataSet.map { it.first.columnType to row[it.first] } + (table.id.columnType to id)
    }
}