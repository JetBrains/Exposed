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

data class ForeignKeyConstraint(val fkName: String,
                                val targetTable: String, val targetColumn:String,
                                val fromTable: String, val fromColumn: String,
                                val updateRule: ReferenceOption, val deleteRule: ReferenceOption) : DdlAware {

    companion object {
        fun from(fromCol: Column<*>): ForeignKeyConstraint {
            require(fromCol.referee != null && (fromCol.onDelete != null || fromCol.onUpdate != null)) { "$fromCol does not reference anything" }
            val targetColumn = fromCol.referee!!
            val t = TransactionManager.current()
            val refName = t.quoteIfNecessary(t.cutIfNecessary("fk_${fromCol.table.tableName}_${fromCol.name}_${targetColumn.name}")).inProperCase()
            return ForeignKeyConstraint(refName,
                    t.identity(targetColumn.table), t.identity(targetColumn),
                    t.identity(fromCol.table), t.identity(fromCol),
                    fromCol.onUpdate ?: ReferenceOption.NO_ACTION,
                    fromCol.onDelete ?: ReferenceOption.NO_ACTION)
        }
    }

    internal val foreignKeyPart = buildString {
        append(" FOREIGN KEY ($fromColumn) REFERENCES $targetTable($targetColumn)")
        if (deleteRule != ReferenceOption.NO_ACTION) {
            append(" ON DELETE $deleteRule")
        }
        if (updateRule != ReferenceOption.NO_ACTION) {
            append(" ON UPDATE $updateRule")
        }
    }

    override fun createStatement() = listOf("ALTER TABLE $fromTable ADD" + (if (fkName.isNotBlank()) " CONSTRAINT $fkName" else "") + foreignKeyPart)

    override fun dropStatement() = listOf("ALTER TABLE $fromTable DROP " +
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

    override fun equals(other: Any?): Boolean {
        if (other !is Index) return false
        return indexName == other.indexName && unique == other.unique && columns == other.columns
    }

    override fun hashCode(): Int {
        return (((indexName.hashCode() * 41) + columns.hashCode()) * 41) + unique.hashCode()
    }

    override fun toString(): String =
            "${if (unique) "Unique " else ""}Index '$indexName' for '${table.nameInDatabaseCase()}' on columns ${columns.joinToString(", ")}"
}
