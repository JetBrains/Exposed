package org.jetbrains.exposed.sql

import java.util.*

class BatchInsertQuery(val table: Table, val _ignore: Boolean = false) {
    val data = ArrayList<LinkedHashMap<Column<*>, Any?>>()

    fun addBatch() {
        data.add(LinkedHashMap())
    }

    operator fun <T> set(column: Column<T>, value: T) {
        val values = data.last()

        if (values.containsKey(column)) {
            error("$column is already initialized")
        }

        values.put(column, column.columnType.valueToDB(value))
    }

    fun execute(transaction: Transaction): List<Int> {
        if (data.isEmpty())
            return emptyList()

        val generatedKeys = ArrayList<Int>()
        val (auto, columns) = table.columns.partition { it.columnType.autoinc }


        val paramsPlaceholder = columns.map{ "?" }.joinToString(", ", prefix = "(", postfix = ")")
        val values = StringBuilder().apply {
            append("VALUES ")

            for (idx in data.indices) {
                if (idx > 0) append(", ")
                append(paramsPlaceholder)
            }
        }.toString()

        val sql = transaction.db.dialect.insert(_ignore,
                transaction.identity(table),
                columns.map{ transaction.identity(it) }, values)

        try {

            transaction.execBatch {
                val stmt = transaction.prepareStatement(sql, auto.map{ transaction.identity(it)})

                val args = arrayListOf<Pair<ColumnType, Any?>>()
                for ((i, d) in data.withIndex()) {
                    columns.mapTo(args) {it.columnType to (d[it] ?: it.defaultValue)}
                    stmt.fillParameters(columns, d, i)
                }

                log(sql, args)

                val count = stmt.executeUpdate()

                assert(count == data.size) { "Number of results don't match number of entries in batch" }

                if (auto.isNotEmpty()) {
                    val rs = stmt.generatedKeys!!
                    while (rs.next()) {
                        generatedKeys.add(rs.getInt(1))
                    }

                    if (generatedKeys.size == 1 && count > 1) {
                        // H2 only returns one last generated keys...
                        var id = generatedKeys.first()

                        while (generatedKeys.size < count) {
                            id -= 1
                            generatedKeys.add(0, id)
                        }
                    }

                    assert(generatedKeys.isEmpty() || generatedKeys.size == count) { "Number of autoincs doesn't match number of batch entries" }
                }
            }
        }
        catch (e: Exception) {
            println("BAD SQL: $sql")
            throw e
        }

        return generatedKeys
    }
}
