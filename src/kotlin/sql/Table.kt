package kotlin.sql

import java.util.ArrayList

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>()
    val primaryKeys: List<Column<*>> = ArrayList<Column<*>>()
    val foreignKeys: List<ForeignKey> = ArrayList<ForeignKey>()

    fun primaryKey(name: String): Column<Int> {
        return column<Int>(name, ColumnType.PRIMARY_KEY)
    }

    fun columnInt(name: String): Column<Int> {
        return column<Int>(name, ColumnType.INT)
    }

    fun columnNullableInt(name: String): Column<Int?> {
        return column<Int?>(name, ColumnType.INT, true)
    }

    fun columnString(name: String): Column<String> {
        return column<String>(name, ColumnType.STRING)
    }

    private fun <T> column(name: String, columnType: ColumnType, nullable: Boolean = false): Column<T> {
        val column = Column<T>(this, name, columnType, nullable)
        if (columnType == ColumnType.PRIMARY_KEY) {
            (primaryKeys as ArrayList<Column<*>>).add(column)
        }
        (tableColumns as ArrayList<Column<*>>).add(column)
        return column
    }

    fun foreignKey(column: Column<*>, table: Table): ForeignKey {
        val foreignKey = ForeignKey(this, column, table)
        (foreignKeys as ArrayList<ForeignKey>).add(foreignKey)
        return foreignKey
    }
}