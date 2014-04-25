package kotlin.sql


open class Column<out T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T> {
    var referee: Column<*>? = null
    var defaultValue: T? = null

    override fun toSQL(queryBuilder: QueryBuilder): String {
        return Session.get().fullIdentity(this);
    }

    val ddl: String
        get() = ddl()

    private fun ddl(): String {
        return "ALTER TABLE ${Session.get().identity(table)} ADD COLUMN ${descriptionDdl()}"
    }

    public fun descriptionDdl(): String {
        val ddl = StringBuilder(Session.get().identity(this)).append(" ")
        val colType = columnType
        when (colType) {
            is EnumerationColumnType<*>,
            is EntityIDColumnType,
            is IntegerColumnType -> ddl.append("INT")

            is DecimalColumnType -> ddl.append("DECIMAL(${colType.scale}, ${colType.precision})")
            is LongColumnType -> ddl.append("BIGINT")
            is StringColumnType -> {
                if (colType.length in 1..255) {
                    ddl.append("VARCHAR(${colType.length})")
                }
                else {
                    ddl.append("TEXT")
                }

                if (colType.collate != null)
                    ddl.append(" COLLATE ${colType.collate}")
            }
            is DateColumnType -> if (colType.time) ddl.append("DATETIME") else ddl.append("DATE")
            is BlobColumnType -> ddl.append("BLOB")
            is BooleanColumnType -> ddl.append("BIT")
            else -> throw IllegalStateException()
        }
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
        (this as? Column<Any>)?.let {
            if (it.defaultValue != null) {
                ddl.append (" DEFAULT ${colType.valueToString(it.defaultValue!!)}")
            }
        }

        return ddl.toString()
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {
}
