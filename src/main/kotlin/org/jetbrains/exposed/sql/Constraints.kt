package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.MysqlDialect
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
    RESTRICT,
    NO_ACTION;

    override fun toString(): String = this.name.replace("_"," ")

    companion object {
        fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
            DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
            DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
            DatabaseMetaData.importedKeyRestrict -> ReferenceOption.RESTRICT
            DatabaseMetaData.importedKeyNoAction -> ReferenceOption.NO_ACTION
            else -> currentDialect.defaultReferenceOption
        }
    }
}

data class ForeignKeyConstraint(val fkName: String, val refereeTable: String, val refereeColumn:String,
                           val referencedTable: String, val referencedColumn: String,
                            val updateRule: ReferenceOption, val deleteRule: ReferenceOption) : DdlAware {

    companion object {
        fun from(column: Column<*>): ForeignKeyConstraint {
            assert(column.referee != null && (column.onDelete != null || column.onUpdate != null)) { "$column does not reference anything" }
            val s = TransactionManager.current()
            return ForeignKeyConstraint("", s.identity(column.referee!!.table),
                    s.identity(column.referee!!), s.identity(column.table), s.identity(column),
                    column.onUpdate ?: ReferenceOption.NO_ACTION,
                    column.onDelete ?: ReferenceOption.NO_ACTION)
        }
    }

    internal val foreignKeyPart = buildString {
        append(" FOREIGN KEY ($referencedColumn) REFERENCES $refereeTable($refereeColumn)")
        if (deleteRule != ReferenceOption.NO_ACTION) {
            append(" ON DELETE $deleteRule")
        }
        if (updateRule != ReferenceOption.NO_ACTION) {
            append(" ON UPDATE $updateRule")
        }
    }

    override fun createStatement() = listOf("ALTER TABLE $referencedTable ADD" + if (fkName.isNotBlank()) " CONSTRAINT $fkName" else "" + foreignKeyPart)

    override fun dropStatement() = listOf("ALTER TABLE $refereeTable DROP " +
            when (currentDialect) {
                is MysqlDialect -> "FOREIGN KEY "
                else -> "CONSTRAINT "
            } + fkName)

    override fun modifyStatement() = dropStatement() + createStatement()

}

data class CheckConstraint(val tableName: String, val checkName: String, val checkOp: String) : DdlAware {

    companion object {
        internal fun from(table: Table, name: String, op: Op<Boolean>): CheckConstraint {
            require(name.isNotBlank())
            val tr = TransactionManager.current()
            val tableName = tr.identity(table)
            val checkOpSQL = op.toSQL(QueryBuilder(false)).replace("$tableName.","")
            return CheckConstraint(tableName, tr.quoteIfNecessary(tr.cutIfNecessary(name)), checkOpSQL)
        }
    }

    internal val checkPart = " CONSTRAINT $checkName CHECK ($checkOp)"

    override fun createStatement(): List<String> {
        return if (currentDialect is MysqlDialect) {
            exposedLogger.warn("Creation of CHECK constraints is not currently supported by MySQL")
            listOf()
        } else listOf("ALTER TABLE $tableName ADD$checkPart")
    }

    override fun dropStatement(): List<String> {
        return if (currentDialect is MysqlDialect) {
            exposedLogger.warn("Deletion of CHECK constraints is not currently supported by MySQL")
            listOf()
        } else listOf("ALTER TABLE $tableName DROP CONSTRAINT $checkName")
    }

    override fun modifyStatement() = dropStatement() + createStatement()
}

data class Index(val columns: List<Column<*>>, val unique: Boolean, val customName: String? = null) : DdlAware {
    val table: Table

    init {
        assert(columns.isNotEmpty())
        assert(columns.groupBy { it.table }.size == 1) { "Columns from different tables can't persist in one index" }
        table = columns.first().table
    }

    val indexName
        get() = customName?: "${table.nameInDatabaseCase()}_${columns.joinToString("_"){it.name.inProperCase()}}" + (if (unique) "_unique".inProperCase() else "")

    override fun createStatement() = listOf(currentDialect.createIndex(this))
    override fun dropStatement() = listOf(currentDialect.dropIndex(table.nameInDatabaseCase(), indexName))


    override fun modifyStatement() = dropStatement() + createStatement()


    fun onlyNameDiffer(other: Index): Boolean =
            indexName != other.indexName && columns == other.columns && unique == other.unique

    override fun toString(): String =
            "${if (unique) "Unique " else ""}Index '$indexName' for '${table.nameInDatabaseCase()}' on columns ${columns.joinToString(", ")}"
}
