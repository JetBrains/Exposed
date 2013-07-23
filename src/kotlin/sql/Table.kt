package kotlin.sql

import java.util.ArrayList

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>()
    val primaryKeys: List<Column<*>> = ArrayList<Column<*>>()
    val foreignKeys: List<ForeignKey> = ArrayList<ForeignKey>()

    fun integer(name: String, autoIncrement: Boolean = false, references: PKColumn<*>? = null): Column<Int> {
        return column<Int>(name, InternalColumnType.INTEGER, false, autoIncrement = autoIncrement, references = references)
    }

    fun integer(name: String, primaryKeyColumnType: PrimaryKeyColumnType, autoIncrement: Boolean = false, references: PKColumn<*>? = null): PKColumn<Int> {
        return pkColumn<Int>(name, InternalColumnType.INTEGER, false, autoIncrement = autoIncrement, references = references)
    }

    fun integer(name: String, nullableColumnType: NullableColumnType, references: PKColumn<*>? = null): Column<Int?> {
        return column<Int?>(name, InternalColumnType.INTEGER, true, references = references)
    }

    fun varchar(name: String, length: Int): Column<String> {
        return column<String>(name, InternalColumnType.STRING, false, length = length)
    }

    fun varchar(name: String, primaryKeyColumnType: PrimaryKeyColumnType, length: Int): PKColumn<String> {
        return pkColumn<String>(name, InternalColumnType.STRING, false, length = length)
    }

    private fun <T> column(name: String, columnType: InternalColumnType, nullable: Boolean, length: Int = 0, autoIncrement: Boolean = false, references: Column<*>? = null): Column<T> {
        val column = Column<T>(this, name, columnType, false, nullable, length, autoIncrement, references)
        if (column.primaryKey) {
            (primaryKeys as ArrayList<Column<*>>).add(column)
        }
        (tableColumns as ArrayList<Column<*>>).add(column)
        return column
    }

    private fun <T> pkColumn(name: String, columnType: InternalColumnType, nullable: Boolean, length: Int = 0, autoIncrement: Boolean = false, references: Column<*>? = null): PKColumn<T> {
        val column = PKColumn<T>(this, name, columnType, true, nullable, length, autoIncrement, references)
        if (column.primaryKey) {
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

    val ddl: String
        get() = ddl()

    private fun ddl(): String {
        var ddl = StringBuilder("CREATE TABLE ${Session.get().identity(this)}")
        if (tableColumns.size > 0) {
            ddl.append(" (")
            var c = 0;
            for (column in tableColumns) {
                ddl.append(Session.get().identity(column)).append(" ")
                when (column.columnType) {
                    InternalColumnType.INTEGER -> ddl.append("INT")
                    InternalColumnType.STRING -> ddl.append("VARCHAR(${column.length})")
                    else -> throw IllegalStateException()
                }
                ddl.append(" ")
                if (column.primaryKey) {
                    ddl.append("PRIMARY KEY ")
                }
                if (column.autoIncrement) {
                    ddl.append(Session.get().autoIncrement(column)).append(" ")
                }
                if (column.nullable) {
                    ddl.append("NULL")
                } else {
                    ddl.append("NOT NULL")
                }
                c++
                if (c < tableColumns.size) {
                    ddl.append(", ")
                }
            }
            ddl.append(")")
        }
        return ddl.toString()
    }
}