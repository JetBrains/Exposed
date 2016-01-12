package org.jetbrains.exposed.sql

import java.util.*
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable

class BatchUpdateQuery(val table: IdTable) {
    val data = ArrayList<Pair<EntityID, HashMap<Column<*>, Any?>>>()

    fun addBatch(id: EntityID) {
        data.add(id to HashMap())
    }

    operator fun <T> set(column: Column<T>, value: T) {
        val values = data.last().second

        if (values.containsKey(column)) {
            error("$column is already initialized")
        }

        values[column] = column.columnType.valueToDB(value)
    }

    fun execute(transaction: Transaction): Int {
        val updateSets = data.filterNot {it.second.isEmpty()}.groupBy { it.second.keys }
        return updateSets.values.fold(0) { acc, set ->
            acc + execute(transaction, set)
        }
    }

    private fun execute(transaction: Transaction, set: Collection<Pair<EntityID, HashMap<Column<*>, Any?>>>): Int {
        val sqlStatement = StringBuilder("UPDATE ${transaction.identity(table)} SET ")

        val columns = set.first().second.keys.toList()

        sqlStatement.append(columns.map {"${transaction.identity(it)} = ?"}.joinToString(", "))
        sqlStatement.append(" WHERE ${transaction.identity(table.id)} = ?")

        val sqlText = sqlStatement.toString()
        return transaction.execBatch {
            val stmt = transaction.prepareStatement(sqlText)
            for ((id, d) in set) {
                log(sqlText, columns.map {it.columnType to d[it]} + (IntegerColumnType() to id))

                val idx = stmt.fillParameters(columns, d)
                stmt.setInt(idx, id.value)
                stmt.addBatch()
            }

            val count = stmt.executeBatch()!!

            assert(count.size == set.size) { "Number of results don't match number of entries in batch" }

            count.sum()
        }
    }
}
