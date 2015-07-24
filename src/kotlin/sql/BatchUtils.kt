package kotlin.sql

import java.sql.PreparedStatement

fun PreparedStatement.fillParameters(args: Iterable<Pair<ColumnType, Any?>>): Int {
    clearParameters()
    var index = 1

    for ((c, v) in args) {
        when (v) {
            null -> setObject(index, null)
            else -> setObject(index, c.valueToDB(v))
        }

        index++
    }

    return index
}

fun PreparedStatement.fillParameters(columns: List<Column<*>>, values: Map<Column<*>, Any?>): Int {
    return fillParameters(columns map {it.columnType to run {
        if (values.containsKey(it))
            values[it]
        else if (it.defaultValue != null)
            it.defaultValue!!
        else if (it.columnType.nullable)
            null
        else
            error("No value specified for column ${it.name} of table ${it.table.tableName}")
    }})
}
