package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement
import java.util.*

class BatchUpdateStatement(val table: IdTable): UpdateStatement(table, null) {
    val data = ArrayList<Pair<EntityID, HashMap<Column<*>, Any?>>>()

    override val firstDataSet: List<Pair<Column<*>, Any?>> get() = data.first().second.toList()

    fun addBatch(id: EntityID) {
        if (data.size < 2 || data.first().second.keys.toList().equals(data.last().second.keys.toList())) {
            data.add(id to HashMap())
        } else {
            val different = data.first().second.keys.intersect(data.last().second.keys)
            error("Some values missing for batch update. Different columns: $different")
        }
    }

    override fun <T, S : T> update(column: Column<T>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction): String {
        return super.prepareSQL(transaction) + " WHERE ${transaction.identity(table.id)} = ?"
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int = executeBatch().sum()

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = data.map { it.second.map { it.key.columnType to it.value } + (table.id.columnType to it.first) }
}

class EntityBatchUpdate(val klass: EntityClass<*>) {

    val data = ArrayList<Pair<EntityID, HashMap<Column<*>, Any?>>>()

    fun addBatch(id: EntityID) {
        data.add(id to HashMap())
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
                it.execute(transaction).first!!
            }
        }
    }
}
