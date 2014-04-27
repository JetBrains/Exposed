package kotlin.sql

import java.sql.PreparedStatement
import java.util.HashMap
import java.util.ArrayList

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
    return fillParameters(columns map {it.columnType to values[it]})
}
