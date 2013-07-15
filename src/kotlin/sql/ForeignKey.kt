package kotlin.sql

class ForeignKey(val table:Table, val column:Column<*>, val referencedTable:Table) {
}