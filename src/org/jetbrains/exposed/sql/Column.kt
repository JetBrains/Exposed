package org.jetbrains.exposed.sql


open class Column<T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T>(), DdlAware {
    var referee: Column<*>? = null
    var onDelete: ReferenceOption? = null
    var defaultValue: T? = null

    override fun equals(other: Any?): Boolean {
        return (other as? Column<*>)?.let {
            it.table == table && it.name == name && it.columnType == columnType
        } ?: false
    }

    override fun hashCode(): Int {
        return table.hashCode()*31 + name.hashCode()
    }

    override fun toString(): String {
        return "${table.javaClass.name}.$name"
    }

    override fun toSQL(queryBuilder: QueryBuilder): String {
        return Transaction.current().fullIdentity(this);
    }

    val ddl: String
        get() = createStatement()

    override fun createStatement(): String = "ALTER TABLE ${Transaction.current().identity(table)} ADD COLUMN ${descriptionDdl()}"

    override fun modifyStatement(): String = "ALTER TABLE ${Transaction.current().identity(table)} MODIFY COLUMN ${descriptionDdl()}"

    override fun dropStatement(): String = Transaction.current().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" }

    fun descriptionDdl(): String {
        val ddl = StringBuilder(Transaction.current().identity(this)).append(" ")
        val colType = columnType
        ddl.append(colType.sqlType())

        if (this is PKColumn<*>) {
            ddl.append(" PRIMARY KEY")
        }
        if (colType.autoinc) {
            ddl.append(" ").append(Transaction.current().autoIncrement(this))
        }
        if (colType.nullable) {
            ddl.append(" NULL")
        } else {
            ddl.append(" NOT NULL")
        }

        if (defaultValue != null) {
            ddl.append (" DEFAULT ${colType.valueToString(defaultValue!!)}")
        }

        return ddl.toString()
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {
}
