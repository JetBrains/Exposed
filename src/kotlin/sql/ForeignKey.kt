package kotlin.sql

class ForeignKey(val table:Table, val column:Column<*>, val referencedTable:Table) {
    val name = "fk_${table.tableName}_${referencedTable.tableName}_${column.name}"
}