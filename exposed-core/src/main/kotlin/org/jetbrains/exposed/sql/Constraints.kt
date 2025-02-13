package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import org.jetbrains.exposed.sql.vendors.inProperCase

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

    /** Returns the list of DDL statements that create this DdlAware instance. */
    val ddl: List<String> get() = createStatement()
}

/**
 * Represents referential actions used by `ON UPDATE` or `ON DELETE` subclauses of a `FOREIGN KEY` constraint clause.
 */
enum class ReferenceOption {
    /** Updates/deletes the referenced parent row, in addition to any rows in the referencing child table. */
    CASCADE,

    /** Updates/deletes the referenced parent row, and sets the column in the referencing child table to `NULL`. */
    SET_NULL,

    /** Prevents updating/deleting the referenced parent row. */
    RESTRICT,

    /** In some, but not all, databases, this action is equivalent to `RESTRICT`. Please check the documentation. */
    NO_ACTION,

    /** Updates/deletes the referenced parent row, and sets the column in the referencing child table to its default value. */
    SET_DEFAULT;

    override fun toString(): String = name.replace("_", " ")
}

/**
 * Represents a foreign key constraint.
 */
data class ForeignKeyConstraint(
    /** Mapping of the foreign key columns in the referencing child table to their referenced parent table columns. */
    val references: Map<Column<*>, Column<*>>,
    private val onUpdate: ReferenceOption?,
    private val onDelete: ReferenceOption?,
    private val name: String?
) : DdlAware {
    constructor(
        target: Column<*>,
        from: Column<*>,
        onUpdate: ReferenceOption?,
        onDelete: ReferenceOption?,
        name: String?
    ) : this(mapOf(from to target), onUpdate, onDelete, name)

    private val tx: Transaction
        get() = CoreTransactionManager.currentTransaction()

    /** The columns of the referenced parent table. */
    val target: LinkedHashSet<Column<*>> = LinkedHashSet(references.values)

    /** The referenced parent table. */
    val targetTable: Table = target.first().table

    /** Name of the referenced parent table. */
    val targetTableName: String
        get() = tx.identity(targetTable)

    /** Names of the referenced parent table columns. */
    private val targetColumns: String
        get() = target.joinToString { tx.identity(it) }

    /** The foreign key columns of the referencing child table. */
    val from: LinkedHashSet<Column<*>> = LinkedHashSet(references.keys)

    /** The referencing child table. */
    val fromTable: Table = from.first().table

    /** Name of the referencing child table. */
    val fromTableName: String
        get() = tx.identity(fromTable)

    /** Names of the foreign key columns from the referencing child table. */
    private val fromColumns: String
        get() = from.joinToString { tx.identity(it) }

    /** Reference option when performing update operations. */
    val updateRule: ReferenceOption?
        get() = onUpdate ?: currentDialectIfAvailable?.defaultReferenceOption

    /** Reference option when performing delete operations. */
    val deleteRule: ReferenceOption?
        get() = onDelete ?: currentDialectIfAvailable?.defaultReferenceOption

    /** Custom foreign key name, if provided. */
    val customFkName: String?
        get() = name

    /** Name of this foreign key constraint. */
    val fkName: String
        @OptIn(InternalApi::class)
        get() = tx.db.identifierManager.cutIfNecessaryAndQuote(
            name ?: (
                "fk_${fromTable.tableNameWithoutSchemeSanitized.replace('.', '_')}_${from.joinToString("_") { it.name }}__" +
                    target.joinToString("_") { it.name }
                )
        ).inProperCase()

    internal val foreignKeyPart: String
        get() = buildString {
            if (fkName.isNotBlank()) {
                append("CONSTRAINT $fkName ")
            }
            append("FOREIGN KEY ($fromColumns) REFERENCES $targetTableName($targetColumns)")

            if (deleteRule != ReferenceOption.NO_ACTION) {
                if (deleteRule == ReferenceOption.RESTRICT && !currentDialect.supportsRestrictReferenceOption) {
                    exposedLogger.warn(
                        "${currentDialect.name} doesn't support FOREIGN KEY with RESTRICT reference option with ON DELETE clause. " +
                            "Please check your $fromTableName table."
                    )
                } else if (deleteRule == ReferenceOption.SET_DEFAULT && !currentDialect.supportsSetDefaultReferenceOption) {
                    exposedLogger.warn(
                        "${currentDialect.name} doesn't support FOREIGN KEY with SET DEFAULT reference option with ON DELETE clause. " +
                            "Please check your $fromTableName table."
                    )
                } else {
                    append(" ON DELETE $deleteRule")
                }
            }

            if (updateRule != ReferenceOption.NO_ACTION) {
                if (!currentDialect.supportsOnUpdate) {
                    exposedLogger.warn("${currentDialect.name} doesn't support FOREIGN KEY with ON UPDATE clause. Please check your $fromTableName table.")
                } else if (updateRule == ReferenceOption.RESTRICT && !currentDialect.supportsRestrictReferenceOption) {
                    exposedLogger.warn(
                        "${currentDialect.name} doesn't support FOREIGN KEY with RESTRICT reference option with ON UPDATE clause. " +
                            "Please check your $fromTableName table."
                    )
                } else if (updateRule == ReferenceOption.SET_DEFAULT && !currentDialect.supportsSetDefaultReferenceOption) {
                    exposedLogger.warn(
                        "${currentDialect.name} doesn't support FOREIGN KEY with SET DEFAULT reference option with ON UPDATE clause. " +
                            "Please check your $fromTableName table."
                    )
                } else {
                    append(" ON UPDATE $updateRule")
                }
            }
        }

    override fun createStatement(): List<String> = listOf("ALTER TABLE $fromTableName ADD $foreignKeyPart")

    override fun modifyStatement(): List<String> = dropStatement() + createStatement()

    override fun dropStatement(): List<String> {
        val constraintType = when (currentDialect) {
            is MysqlDialect -> "FOREIGN KEY"
            else -> "CONSTRAINT"
        }
        return listOf("ALTER TABLE $fromTableName DROP $constraintType $fkName")
    }

    /** Returns the parent table column that is referenced by the [from] column in the child table. */
    fun targetOf(from: Column<*>): Column<*>? = references[from]

    operator fun plus(other: ForeignKeyConstraint): ForeignKeyConstraint {
        return copy(references = references + other.references)
    }

    override fun toString() = "ForeignKeyConstraint(fkName='$fkName')"
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

    private val DatabaseDialect.cannotAlterCheckConstraint: Boolean
        get() = this is SQLiteDialect || (this as? MysqlDialect)?.isMysql8 == false

    override fun createStatement(): List<String> {
        return if (currentDialect.cannotAlterCheckConstraint) {
            exposedLogger.warn("Creation of CHECK constraints is not currently supported by ${currentDialect.name}")
            listOf()
        } else {
            listOf("ALTER TABLE $tableName ADD $checkPart")
        }
    }

    override fun modifyStatement(): List<String> = dropStatement() + createStatement()

    override fun dropStatement(): List<String> {
        return if (currentDialect.cannotAlterCheckConstraint) {
            exposedLogger.warn("Deletion of CHECK constraints is not currently supported by ${currentDialect.name}")
            listOf()
        } else {
            listOf("ALTER TABLE $tableName DROP CONSTRAINT $checkName")
        }
    }

    companion object {
        internal fun from(table: Table, name: String, op: Op<Boolean>): CheckConstraint {
            require(name.isNotBlank()) { "Check constraint name cannot be blank" }
            val tr = CoreTransactionManager.currentTransaction()
            val identifierManager = tr.db.identifierManager
            val tableName = tr.identity(table)
            val checkOpSQL = op.toString().replace("$tableName.", "")
            return CheckConstraint(tableName, identifierManager.cutIfNecessaryAndQuote(name), checkOpSQL)
        }
    }
}

/** A conditional expression used as a filter when creating a partial index. */
typealias FilterCondition = (SqlExpressionBuilder.() -> Op<Boolean>)?

/**
 * Represents an index.
 */
data class Index(
    /** Columns that are part of the index. */
    val columns: List<Column<*>>,
    /** Whether the index in unique or not. */
    val unique: Boolean,
    /** Optional custom name for the index. */
    val customName: String? = null,
    /** Optional custom index type (e.g, BTREE or HASH) */
    val indexType: String? = null,
    /** Partial index filter condition */
    val filterCondition: Op<Boolean>? = null,
    /** Functions that are part of the index. */
    val functions: List<ExpressionWithColumnType<*>>? = null,
    /** Table where the functional index should be defined. */
    val functionsTable: Table? = null
) : DdlAware {
    /** Table where the index is defined. */
    val table: Table

    /** Name of the index. */
    val indexName: String
        get() = customName ?: buildString {
            append(table.nameInDatabaseCaseUnquoted())
            append('_')
            append(columns.joinToString("_") { it.name })
            functions?.let { f ->
                if (columns.isNotEmpty()) append('_')
                append(f.joinToString("_") { it.toString().substringBefore("(").lowercase() })
            }
            if (unique) {
                append("_unique")
            }
        }.inProperCase()

    init {
        require(columns.isNotEmpty() || functions?.isNotEmpty() == true) { "At least one column or function is required to create an index" }
        val columnsTable = if (columns.isNotEmpty()) {
            val table = columns.distinctBy { it.table }.singleOrNull()?.table
            requireNotNull(table) { "Columns from different tables can't persist in one index" }
            table
        } else {
            null
        }
        if (functions?.isNotEmpty() == true) {
            requireNotNull(functionsTable) { "functionsTable argument must also be provided if functions are defined to create an index" }
        }
        this.table = columnsTable ?: functionsTable!!
    }

    override fun createStatement(): List<String> = listOf(currentDialect.createIndex(this))
    override fun modifyStatement(): List<String> = dropStatement() + createStatement()
    override fun dropStatement(): List<String> = listOf(
        currentDialect.dropIndex(table.nameInDatabaseCase(), indexName, unique, filterCondition != null || functions != null)
    )

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
