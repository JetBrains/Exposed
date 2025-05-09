package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.dao.id.CompositeID
import org.jetbrains.exposed.v1.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IdTable

/**
 * Represents the SQL statement that batch updates rows of a table.
 *
 * @param table Identity table to update values from.
 */
open class BatchUpdateStatement(val table: IdTable<*>) : UpdateStatement(table, null) {
    /** The mappings of columns to update with their updated values for each entity in the batch. */
    val data = ArrayList<Pair<EntityID<*>, Map<Column<*>, Any?>>>()
    override val firstDataSet: List<Pair<Column<*>, Any?>> get() = data.first().second.toList()

    /**
     * Adds the specified entity [id] to the current list of update statements, using the mapping of columns to update
     * provided for this `BatchUpdateStatement`.
     */
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

        @OptIn(InternalApi::class)
        if (data.isNotEmpty()) {
            data[data.size - 1] = lastBatch!!.copy(second = values.toMap())
            values.clear()
            hasBatchedValues = true
        }
        @OptIn(InternalApi::class)
        data.add(id to values)
    }

    override fun <T, S : T?> update(column: Column<T>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val updateSql = super.prepareSQL(transaction, prepared)
        val idEqCondition = if (table is CompositeIdTable) {
            table.idColumns.joinToString(separator = " AND ") { "${transaction.identity(it)} = ?" }
        } else {
            "${transaction.identity(table.id)} = ?"
        }
        return "$updateSql WHERE $idEqCondition"
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = data.map { (id, row) ->
        val idArgs = (id.value as? CompositeID)?.values?.map {
            it.key.columnType to it.value
        } ?: listOf(table.id.columnType to id)
        firstDataSet.map { it.first.columnType to row[it.first] } + idArgs
    }
}
