package kotlin.sql

import java.util.ArrayList

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns = ArrayList<Column<*>>()
    val primaryKeys  = ArrayList<Column<*>>()
    val foreignKeys  = ArrayList<ForeignKey>()

    fun <T> Column<T>.primaryKey(): PKColumn<T> {
        tableColumns.remove(this)
        val answer = PKColumn<T>(table, name, columnType)
        primaryKeys.add(answer)
        tableColumns.add(answer)
        return answer
    }

    fun integer(name: String): Column<Int> {
        val answer = Column<Int>(this, name, IntegerColumnType())
        tableColumns.add(answer)
        return answer
    }

    fun varchar(name: String, length: Int): Column<String> {
        val answer = Column<String>(this, name, StringColumnType(length))
        tableColumns.add(answer)
        return answer
    }

    fun <C:Column<Int>> C.autoIncrement(): C {
        (columnType as IntegerColumnType).autoinc = true
        return this
    }

    fun <T, C:Column<T>> C.references(ref: PKColumn<T>): C {
        referee = ref
        return this
    }

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        columnType.nullable = true
        return this
    }

    internal fun foreignKey(column: Column<*>, table: Table): ForeignKey {
        val foreignKey = ForeignKey(this, column, table)
        foreignKeys.add(foreignKey)
        return foreignKey
    }

    class object {
        internal val setPairs = ThreadLocal<Array<Pair<Column<*>, *>>>()
    }

    internal fun set(vararg pairs: Pair<Column<*>, *>) {
        setPairs.set(pairs);
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
                val colType = column.columnType
                when (colType) {
                    is IntegerColumnType -> ddl.append("INT")
                    is StringColumnType -> ddl.append("VARCHAR(${colType.length})")
                    else -> throw IllegalStateException()
                }
                ddl.append(" ")
                if (column is PKColumn<*>) {
                    ddl.append("PRIMARY KEY ")
                }
                if (colType is IntegerColumnType && colType.autoinc) {
                    ddl.append(Session.get().autoIncrement(column)).append(" ")
                }
                if (colType.nullable) {
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
