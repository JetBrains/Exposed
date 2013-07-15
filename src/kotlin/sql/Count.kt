package kotlin.sql

class Count(val column: Column<*>): Column<Int>(column.table, "COUNT($column)", ColumnType.INT, true) {
    override fun toString(): String {
        return "COUNT($column)"
    }
}