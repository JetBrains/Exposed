package kotlin.sql


open class Column<T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T>, DdlAware {
    var referee: Column<*>? = null
    var onDelete: ReferenceOption? = null
    var defaultValue: T? = null

    override fun toSQL(queryBuilder: QueryBuilder): String {
        return Session.get().fullIdentity(this);
    }

    val ddl: String
        get() = createStatement()

    override fun createStatement(): String = "ALTER TABLE ${Session.get().identity(table)} ADD COLUMN ${descriptionDdl()}"

    override fun modifyStatement(): String = "ALTER TABLE ${Session.get().identity(table)} MODIFY COLUMN ${descriptionDdl()}"

    override fun dropStatement(): String = Session.get().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" }

    public fun descriptionDdl(): String {
        val ddl = StringBuilder(Session.get().identity(this)).append(" ")
        val colType = columnType
        ddl.append(colType.sqlType())

        if (this is PKColumn<*>) {
            ddl.append(" PRIMARY KEY")
        }
        if (colType.autoinc) {
            ddl.append(" ").append(Session.get().autoIncrement(this))
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