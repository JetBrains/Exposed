package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.sql.DatabaseMetaData

/**
 * Common interface for database objects that can be created, modified and dropped.
 */
interface DdlAware {
    /** Returns the list of DDL statements that create this object. */
    fun createStatement(): List<String>

    /** Returns the list of DDL statements that modify this object. */
    fun modifyStatement(): List<String>

    /** Returns the list of DDL statements that drops this object. */
    fun dropStatement(): List<String>
}

/**
 * Represents reference constraint actions.
 * Read [Referential actions](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html#foreign-key-referential-actions) from MySQL docs
 * or on [StackOverflow](https://stackoverflow.com/a/6720458/813981)
 */
enum class ReferenceOption {
    CASCADE,
    SET_NULL,
    RESTRICT,
    NO_ACTION;

    override fun toString(): String = name.replace("_", " ")

    companion object {
        /** Returns the corresponding [ReferenceOption] for the specified [refOption] from JDBC. */
        fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
            DatabaseMetaData.importedKeyCascade -> CASCADE
            DatabaseMetaData.importedKeySetNull -> SET_NULL
            DatabaseMetaData.importedKeyRestrict -> RESTRICT
            DatabaseMetaData.importedKeyNoAction -> NO_ACTION
            else -> currentDialect.defaultReferenceOption
        }
    }
}

/**
 * Represents a foreign key constraint.
 */
data class ForeignKeyConstraint(
        val target: Column<*>,
        val from: Column<*>,
        private val onUpdate: ReferenceOption?,
        private val onDelete: ReferenceOption?,
        private val name: String?
) : DdlAware {
    private val tx: Transaction
        get() = TransactionManager.current()
    /** Name of the child table. */
    val targetTable: String
        get() = tx.identity(target.table)
    /** Name of the foreign key column. */
    val targetColumn: String
        get() = tx.identity(target)
    /** Name of the parent table. */
    val fromTable: String
        get() = tx.identity(from.table)
    /** Name of the key column from the parent table. */
    val fromColumn
        get() = tx.identity(from)
    /** Reference option when performing update operations. */
    val updateRule: ReferenceOption?
        get() = onUpdate ?: currentDialectIfAvailable?.defaultReferenceOption
    /** Reference option when performing delete operations. */
    val deleteRule: ReferenceOption?
        get() = onDelete ?: currentDialectIfAvailable?.defaultReferenceOption
    /** Name of this constraint. */
    val fkName: String
        get() = tx.db.identifierManager.cutIfNecessaryAndQuote(
                name ?: "fk_${from.table.tableNameWithoutScheme}_${from.name}_${target.name}"
        ).inProperCase()
    internal val foreignKeyPart: String get() = buildString {
        if (fkName.isNotBlank()) {
            append("CONSTRAINT $fkName ")
        }
        append("FOREIGN KEY ($fromColumn) REFERENCES $targetTable($targetColumn)")
        if (deleteRule != ReferenceOption.NO_ACTION) {
            append(" ON DELETE $deleteRule")
        }
        if (updateRule != ReferenceOption.NO_ACTION) {
            if (currentDialect is OracleDialect) {
                exposedLogger.warn("Oracle doesn't support FOREIGN KEY with ON UPDATE clause. Please check your $fromTable table.")
            } else {
                append(" ON UPDATE $updateRule")
            }
        }
    }

    override fun createStatement(): List<String> = listOf("ALTER TABLE $fromTable ADD $foreignKeyPart")

    override fun modifyStatement(): List<String> = dropStatement() + createStatement()

    override fun dropStatement(): List<String> {
        val constraintType = when (currentDialect) {
            is MysqlDialect -> "FOREIGN KEY"
            else -> "CONSTRAINT"
        }
        return listOf("ALTER TABLE $fromTable DROP $constraintType $fkName")
    }
}

/**
 * Represents a check constraint.
 */
data class CheckConstraint(
    /** Name of the table where the constraint is defined. */
    val tableName: String,
    /** Name of the check constraint. */
    val checkName: String,
    /** Boolean expression used for the check constraint. */
    val checkOp: String
) : DdlAware {

    internal val checkPart = "CONSTRAINT $checkName CHECK ($checkOp)"

    override fun createStatement(): List<String> {
        return if (currentDialect is MysqlDialect) {
            exposedLogger.warn("Creation of CHECK constraints is not currently supported by MySQL")
            listOf()
        } else {
            listOf("ALTER TABLE $tableName ADD $checkPart")
        }
    }

    override fun modifyStatement(): List<String> = dropStatement() + createStatement()

    override fun dropStatement(): List<String> {
        return if (currentDialect is MysqlDialect) {
            exposedLogger.warn("Deletion of CHECK constraints is not currently supported by MySQL")
            listOf()
        } else {
            listOf("ALTER TABLE $tableName DROP CONSTRAINT $checkName")
        }
    }

    companion object {
        internal fun from(table: Table, name: String, op: Op<Boolean>): CheckConstraint {
            require(name.isNotBlank()) { "Check constraint name cannot be blank" }
            val tr = TransactionManager.current()
            val identifierManager = tr.db.identifierManager
            val tableName = tr.identity(table)
            val checkOpSQL = op.toString().replace("$tableName.", "")
            return CheckConstraint(tableName, identifierManager.cutIfNecessaryAndQuote(name), checkOpSQL)
        }
    }
}

/**
 * Represents an index.
 */
data class Index(
    /** Columns that are part of the index. */
    val columns: List<Column<*>>,
    /** Whether the index in unique or not. */
    val unique: Boolean,
    /** Optional custom name for the index. */
    val customName: String? = null
) : DdlAware {
    /** Table where the index is defined. */
    val table: Table
    /** Name of the index. */
    val indexName: String
        get() = customName ?: buildString {
            append(table.nameInDatabaseCase())
            append('_')
            append(columns.joinToString("_") { it.name }.inProperCase())
            if (unique) {
                append("_unique".inProperCase())
            }
        }

    init {
        require(columns.isNotEmpty()) { "At least one column is required to create an index" }
        val table = columns.distinctBy { it.table }.singleOrNull()?.table
        requireNotNull(table) { "Columns from different tables can't persist in one index" }
        this.table = table
    }

    override fun createStatement(): List<String> = listOf(currentDialect.createIndex(this))
    override fun modifyStatement(): List<String> = dropStatement() + createStatement()
    override fun dropStatement(): List<String> = listOf(currentDialect.dropIndex(table.nameInDatabaseCase(), indexName))

    /** Returns `true` if the [other] index has the same columns and uniqueness as this index, but a different name, `false` otherwise */
    fun onlyNameDiffer(other: Index): Boolean = indexName != other.indexName && columns == other.columns && unique == other.unique

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Index) return false

        if (indexName != other.indexName) return false
        if (columns != other.columns) return false
        if (unique != other.unique) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indexName.hashCode()
        result = 31 * result + columns.hashCode()
        result = 31 * result + unique.hashCode()
        return result
    }

    override fun toString(): String =
        "${if (unique) "Unique " else ""}Index '$indexName' for '${table.nameInDatabaseCase()}' on columns ${columns.joinToString()}"
}
