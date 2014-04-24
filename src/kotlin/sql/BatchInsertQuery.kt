package kotlin.sql

import java.util.LinkedHashMap
import kotlin.dao.EntityID
import java.util.ArrayList
import kotlin.dao.EntityCache
import java.sql.PreparedStatement

class BatchInsertQuery(val table: Table) {
    val data = ArrayList<LinkedHashMap<Column<*>, Any?>>()

    fun addBatch() {
        data.add(LinkedHashMap())
    }

    fun <T> set(column: Column<T>, value: T) {
        val values = data.last!!

        if (values containsKey column) {
            error("$column is already initialized")
        }

        values.put(column, if (value == null) null else {
            when(column.columnType) {
                is EnumerationColumnType<*> -> (value as Enum<*>).ordinal()
                is EntityIDColumnType -> (value as EntityID).value
                else -> value
            }
        })
    }

    fun execute(session: Session): List<Int> {
        val generatedKeys = ArrayList<Int>()
        val (auto, columns) = table.columns.partition { it.columnType.autoinc }

        var sql = StringBuilder("INSERT INTO ${session.identity(table)}")

        sql.append(" (")
        sql.append((columns map { session.identity(it) }).makeString(", ", "", ""))
        sql.append(") ")

        sql.append("VALUES (")
        sql.append((columns map { "?" }). makeString(", ", "", ""))

        sql.append(") ")
        try {
            val sqlText = sql.toString()

            session.exec(sqlText) {
                val stmt = session.prepareStatement(sqlText, auto map {session.identity(it)})
                for (d in data) {
                    stmt.fillParameters(columns, d)
                    stmt.addBatch()
                }

                val count = stmt.executeBatch()!!

                assert(count.size == data.size, "Number of results don't match number of entries in batch")

                EntityCache.getOrCreate(session).clearReferrersCache()

                val rs = stmt.getGeneratedKeys()!!
                while (rs.next()) {
                    generatedKeys.add(rs.getInt(1))
                }

                if (generatedKeys.size == 1 && count.size > 1) {
                    // H2 only returns one last generated keys...
                    var id = generatedKeys.first()

                    while (generatedKeys.size < count.size) {
                        generatedKeys.add(0, --id)
                    }
                }

                assert(generatedKeys.size == 0 || generatedKeys.size == count.size, "Number of autoincs doesn't match number of batch entries")
            }
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }

        return generatedKeys
    }

    private fun PreparedStatement.fillParameters(columns: List<Column<*>>, values: Map<Column<*>, Any?>) {
        clearParameters()
        var index = 1
        for (c in columns) {
            val value = values[c]
            val sqlType = c.columnType

            when (value) {
                null -> setObject(index, null)
                else -> sqlType.setParameter(this, index, value)
            }

            index++
        }
    }
}
