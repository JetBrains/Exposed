package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Transaction
import java.sql.PreparedStatement
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

    override fun <T, S:T?> update(column: Column<T>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction): String =
         "${super.prepareSQL(transaction)} WHERE ${transaction.identity(table.id)} = ?"

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int = if (data.size == 1) executeUpdate() else executeBatch().sum()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = data.map { (id, row) ->
        firstDataSet.map { it.first.columnType to row[it.first] } + (table.id.columnType to id)
    }
}

class EntityBatchUpdate(val klass: EntityClass<*, Entity<*>>) {

    private val data = ArrayList<Pair<EntityID<*>, SortedMap<Column<*>, Any?>>>()

    fun addBatch(id: EntityID<*>) {
        if (id.table != klass.table) error("Table from Entity ID ${id.table.tableName} differs from entity class ${klass.table.tableName}")
        data.add(id to TreeMap())
    }

    operator fun set(column: Column<*>, value: Any?) {
        val values = data.last().second

        if (values.containsKey(column)) {
            error("$column is already initialized")
        }

        values[column] = column.columnType.valueToDB(value)
    }

    fun execute(transaction: Transaction): Int {
        val updateSets = data.filterNot {it.second.isEmpty()}.groupBy { it.second.keys }
        return updateSets.values.fold(0) { acc, set ->
            acc + BatchUpdateStatement(klass.table).let {
                it.data.addAll(set)
                it.execute(transaction)!!
            }
        }
    }
}
