package kotlin.sql

import java.sql.DatabaseMetaData

interface DdlAware {
    fun createStatement(): String
    fun modifyStatement(): String
    fun dropStatement(): String
}

enum class ReferenceOption {
    CASCADE,
    SET_NULL,
    RESTRICT; //default

    override fun toString(): String {
        return this.name.replace("_"," ")
    }

    companion object {
        public fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
            DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
            DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
            DatabaseMetaData.importedKeyRestrict -> ReferenceOption.RESTRICT
            else -> ReferenceOption.RESTRICT
        }
    }
}

data class ForeignKeyConstraint(val fkName: String, val refereeTable: String, val refereeColumn:String,
                           val referencedTable: String, val referencedColumn: String, var deleteRule: ReferenceOption?) : DdlAware {

    companion object {
        fun from(column: Column<*>): ForeignKeyConstraint {
            assert(column.referee !== null) { "$column does not reference anything" }
            val s = Transaction.current()
            return ForeignKeyConstraint("", s.identity(column.referee!!.table), s.identity(column.referee!!), s.identity(column.table), s.identity(column), column.onDelete)
        }
    }

    override fun createStatement(): String {
        var alter = StringBuilder("ALTER TABLE $referencedTable ADD")
        if (!fkName.isBlank()) alter.append(" CONSTRAINT $fkName")
        alter.append(" FOREIGN KEY ($referencedColumn) REFERENCES $refereeTable($refereeColumn)")

        deleteRule?.let { onDelete ->
            alter.append(" ON DELETE $onDelete")
        }
        return alter.toString()
    }

    override fun dropStatement(): String = "ALTER TABLE $refereeTable DROP FOREIGN KEY $fkName"

    override fun modifyStatement(): String = "${dropStatement()};\n${createStatement()}"

}

data class Index(val indexName: String, val tableName: String, val columns: List<String>, val unique: Boolean) : DdlAware {
    companion object {
        fun forColumns(vararg columns: Column<*>, unique: Boolean): Index {
            assert(columns.isNotEmpty())
            assert(columns.groupBy { it.table }.size == 1) { "Columns from different tables can't persist in one index" }
            val s = Transaction.current()
            val indexName = "${s.identity(columns.first().table)}_${columns.map { s.identity(it) }.joinToString("_")}" + (if (unique) "_unique" else "")
            return Index(indexName, s.identity(columns.first().table), columns.map { s.identity(it) }, unique)
        }
    }

    override fun createStatement(): String {
        var alter = StringBuilder()
        val indexType = if (unique) "UNIQUE " else ""
        alter.append("CREATE ${indexType}INDEX $indexName ON $tableName ")
        columns.joinTo(alter, ", ", "(", ")")
        return alter.toString()
    }

    override fun dropStatement(): String {
        val keyWord = if (Transaction.current().db.vendor == DatabaseVendor.MySql) "INDEX" else "CONSTRAINT"
        return "ALTER TABLE $tableName DROP $keyWord $indexName"
    }


    override fun modifyStatement() = "${dropStatement()};\n${createStatement()}"


    fun onlyNameDiffer(other: Index): Boolean {
        return indexName != other.indexName && columns.equals(other.columns) && unique == other.unique
    }

    override fun toString(): String {
        return "${if (unique) "Unique " else ""}Index '$indexName' for '$tableName' on columns ${columns.joinToString(", ")}"
    }
}
