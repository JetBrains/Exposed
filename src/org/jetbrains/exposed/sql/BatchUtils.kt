package org.jetbrains.exposed.sql

import java.sql.PreparedStatement

fun PreparedStatement.fillParameters(args: Iterable<Pair<ColumnType, Any?>>, offset: Int = 0): Int {
    if (offset == 0) {
        clearParameters()
    }

    var index = offset * args.count() + 1

    for ((c, v) in args) {
        when (v) {
            null -> setObject(index, null)
            else -> setObject(index, c.valueToDB(v))
        }

        index++
    }

    return index
}

fun PreparedStatement.fillParameters(columns: List<Column<*>>, values: Map<Column<*>, Any?>, offset: Int = 0): Int {
    return fillParameters(columns.map { it.columnType to run {
        if (values.containsKey(it))
            values[it]
        else if (it.defaultValue != null)
            it.defaultValue!!
        else if (it.columnType.nullable)
            null
        else
            error("No value specified for column ${it.name} of table ${it.table.tableName}")
    }}, offset)
}
