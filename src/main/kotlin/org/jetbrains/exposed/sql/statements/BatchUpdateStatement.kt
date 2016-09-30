package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Transaction
import java.sql.PreparedStatement
import java.util.*

class BatchUpdateStatement(val table: IdTable<*>): UpdateStatement(table, null) {
    val data = ArrayList<Pair<EntityID<*>, SortedMap<Column<*>, Any?>>>()

    override val firstDataSet: List<Pair<Column<*>, Any?>> get() = data.first().second.toList()

    fun addBatch(id: EntityID<*>) {
        if (data.size < 2 || data.first().second.keys.toList() == data.last().second.keys.toList()) {
            data.add(id to TreeMap())
        } else {
            val different = data.first().second.keys.intersect(data.last().second.keys)
            error("Some values missing for batch update. Different columns: $different")
        }
    }

    override fun <S> update(column: Column<S>, value: Expression<S>) = error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction): String {
        return super.prepareSQL(transaction) + " WHERE ${transaction.identity(table.id)} = ?"
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int = if (data.size == 1) executeUpdate() else executeBatch().sum()

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = data.map { it.second.map { it.key.columnType to it.value } + (table.id.columnType to it.first) }
}

class EntityBatchUpdate<ID:Any>(val klass: EntityClass<in ID, Entity<in ID>>) {

    private val data = ArrayList<Pair<EntityID<ID>, SortedMap<Column<*>, Any?>>>()

    fun addBatch(id: EntityID<ID>) {
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
