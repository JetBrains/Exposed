package kotlin.sql

import java.sql.PreparedStatement

private fun PreparedStatement.fillParameters(columns: List<Column<*>>, values: Map<Column<*>, Any?>): Int {
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

    return index
}
