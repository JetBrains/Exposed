package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.sql.DatabaseMetaData

interface DdlAware {
    fun createStatement(): List<String>
    fun modifyStatement(): List<String>
    fun dropStatement(): List<String>
}

enum class ReferenceOption {
    CASCADE,
    SET_NULL,
    RESTRICT; //default

    override fun toString(): String {
        return this.name.replace("_"," ")
    }

    companion object {
        fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
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
            val s = TransactionManager.current()
            return ForeignKeyConstraint("", s.identity(column.referee!!.table).inProperCase(), s.identity(column.referee!!).inProperCase(), s.identity(column.table).inProperCase(), s.identity(column).inProperCase(), column.onDelete)
        }
    }

    internal val foreignKeyPart = buildString {
        append(" FOREIGN KEY ($referencedColumn) REFERENCES $refereeTable($refereeColumn)")

        deleteRule?.let { onDelete ->
            append(" ON DELETE $onDelete")
        }
    }

    override fun createStatement() = listOf("ALTER TABLE $referencedTable ADD" + if (fkName.isNotBlank()) " CONSTRAINT $fkName" else "" + foreignKeyPart)

    override fun dropStatement() = listOf("ALTER TABLE $refereeTable DROP FOREIGN KEY $fkName")

    override fun modifyStatement() = dropStatement() + createStatement()

}

data class Index(val indexName: String, val tableName: String, val columns: List<String>, val unique: Boolean) : DdlAware {
    companion object {
        fun forColumns(vararg columns: Column<*>, unique: Boolean): Index {
            assert(columns.isNotEmpty())
            assert(columns.groupBy { it.table }.size == 1) { "Columns from different tables can't persist in one index" }
            val indexName = "${columns.first().table.nameInDatabaseCase()}_${columns.joinToString("_"){it.name.inProperCase()}}" + (if (unique) "_unique".inProperCase() else "")
            return Index(indexName, columns.first().table.nameInDatabaseCase(), columns.map { it.name.inProperCase() }, unique)
        }
    }

    override fun createStatement() = listOf(currentDialect.createIndex(unique, tableName, indexName, columns))
    override fun dropStatement() = listOf(currentDialect.dropIndex(tableName, indexName))


    override fun modifyStatement() = dropStatement() + createStatement()


    fun onlyNameDiffer(other: Index): Boolean {
        return indexName != other.indexName && columns == other.columns && unique == other.unique
    }

    override fun toString(): String {
        return "${if (unique) "Unique " else ""}Index '$indexName' for '$tableName' on columns ${columns.joinToString(", ")}"
    }
}
