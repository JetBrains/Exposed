package kotlinx.sql

import java.util.*
import kotlinx.dao.*

class BatchUpdateQuery(val table: IdTable) {
    val data = ArrayList<Pair<EntityID, HashMap<Column<*>, Any?>>>()

    fun addBatch(id: EntityID) {
        data.add(id to HashMap())
    }

    fun <T> set(column: Column<T>, value: T) {
        val values = data.last().second

        if (values containsKey column) {
            error("$column is already initialized")
        }

        values[column] = column.columnType.valueToDB(value)
    }

    fun execute(session: Session): Int {
        val updateSets = data filterNot {it.second.isEmpty()} groupBy { it.second.keySet() }
        return updateSets.values().fold(0) { acc, set ->
            acc + execute(session, set)
        }
    }

    private fun execute(session: Session, set: Collection<Pair<EntityID, HashMap<Column<*>, Any?>>>): Int {
        val sqlStatement = StringBuilder("UPDATE ${session.identity(table)} SET ")

        val columns = set.first().second.keySet().toList()

        sqlStatement.append(columns.map {"${session.identity(it)} = ?"}.join(", "))
        sqlStatement.append(" WHERE ${session.identity(table.id)} = ?")

        val sqlText = sqlStatement.toString()
        return session.execBatch {
            val stmt = session.prepareStatement(sqlText)
            for ((id, d) in set) {
                log(sqlText, columns.map {it.columnType to d[it]} + (IntegerColumnType() to id))

                val idx = stmt.fillParameters(columns, d)
                stmt.setInt(idx, id.value)
                stmt.addBatch()
            }

            val count = stmt.executeBatch()!!

            assert(count.size == set.size, "Number of results don't match number of entries in batch")

            count.sum()
        }
    }
}
