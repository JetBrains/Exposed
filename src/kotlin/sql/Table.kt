package kotlin.sql

import java.util.ArrayList

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>()
    val primaryKeys: List<Column<*>> = ArrayList<Column<*>>()
    val foreignKeys: List<ForeignKey> = ArrayList<ForeignKey>()

    fun id(name: String, autoIncrement: Boolean = false): Column<Int> {
        return column<Int>(name, ColumnType.PRIMARY_KEY, false, autoIncrement = autoIncrement)
    }

    fun integer(name: String, references: Column<*>? = null): Column<Int> {
        return column<Int>(name, ColumnType.INT, false, references = references)
    }

    fun integerNullable(name: String, references: Column<*>? = null): Column<Int?> {
        val column = column<Int?>(name, ColumnType.INT, nullable = true, autoIncrement = false, references = references)
        if (references != null) {
            foreignKey(column, references.table)
        }
        return column
    }

    fun varchar(name: String, length: Int): Column<String> {
        return column<String>(name, ColumnType.STRING, false, length = length)
    }

    private fun <T> column(name: String, columnType: ColumnType, nullable: Boolean, length: Int = 0, autoIncrement: Boolean = false, references: Column<*>? = null): Column<T> {
        val column = Column<T>(this, name, columnType, nullable, length, autoIncrement, references)
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