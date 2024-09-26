package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*

private val comparator: Comparator<Column<*>> = compareBy({ it.table.tableName }, { it.name })

/**
 * Represents a column.
 */
class Column<T>(
    /** Table where the columns are declared. */
    val table: Table,
    /** Name of the column. */
    val name: String,
    /** Data type of the column. */
    override val columnType: IColumnType<T & Any>
) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    /** The foreign key constraint on this column, or `null` if the column is not referencing. */
    var foreignKey: ForeignKeyConstraint? = null

    /** Returns the column that this column references. */
    val referee: Column<*>?
        get() = foreignKey?.targetOf(this)

    /** Returns the column that this column references, cast as a column of type [S], or `null` if the cast fails. */
    @Suppress("UNCHECKED_CAST")
    fun <S : T> referee(): Column<S>? = referee as? Column<S>

    /** Returns the function that calculates the default value for this column. */
    var defaultValueFun: (() -> T)? = null
    internal var dbDefaultValue: Expression<T>? = null

    /** Returns the default value for this column on the database-side. */
    fun defaultValueInDb() = dbDefaultValue

    internal var isDatabaseGenerated: Boolean = false

    /** Returns whether this column's value will be generated in the database. */
    fun isDatabaseGenerated() = isDatabaseGenerated

    internal var extraDefinitions = mutableListOf<Any>()

    /** Appends the SQL representation of this column to the specified [queryBuilder]. */
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = TransactionManager.current().fullIdentity(this@Column, queryBuilder)

    /** Returns the column name in proper case. */
    fun nameInDatabaseCase(): String = name.inProperCase()

    /**
     * Returns the column name with wrapping double-quotation characters removed.
     *
     * **Note** If used with MySQL or MariaDB, the column name is returned unchanged, since these databases use a
     * backtick character as the identifier quotation.
     */
    fun nameUnquoted(): String = if (currentDialect is MysqlDialect) name else name.trim('\"')

    private val isLastColumnInPK: Boolean
        get() = this == table.primaryKey?.columns?.last()

    internal val isPrimaryConstraintWillBeDefined: Boolean
        get() = when {
            currentDialect is SQLiteDialect && columnType.isAutoInc -> false
            table.isCustomPKNameDefined() -> isLastColumnInPK
            isOneColumnPK() -> false
            else -> isLastColumnInPK
        }

    override fun createStatement(): List<String> {
        val alterTablePrefix = "ALTER TABLE ${TransactionManager.current().identity(table)} ADD"
        val isH2withCustomPKConstraint = currentDialect is H2Dialect && isLastColumnInPK
        val isOracle = currentDialect is OracleDialect
        val columnDefinition = when {
            isPrimaryConstraintWillBeDefined && isLastColumnInPK && !isH2withCustomPKConstraint && !isOracle ->
                descriptionDdl(false) + ", ADD ${table.primaryKeyConstraint()}"

            isH2withCustomPKConstraint -> descriptionDdl(true)
            else -> descriptionDdl(false)
        }

        val addConstr = if (isH2withCustomPKConstraint || (isOracle && isPrimaryConstraintWillBeDefined)) {
            "$alterTablePrefix ${table.primaryKeyConstraint()}"
        } else {
            null
        }
        return listOfNotNull("$alterTablePrefix $columnDefinition", addConstr)
    }

    /** Returns the SQL statements that modify this column according to differences in the provided [ColumnDiff]. */
    fun modifyStatements(columnDiff: ColumnDiff): List<String> = currentDialect.modifyColumn(this, columnDiff)

    override fun modifyStatement(): List<String> = currentDialect.modifyColumn(this, ColumnDiff.AllChanged)

    override fun dropStatement(): List<String> {
        val tr = TransactionManager.current()
        return listOf("ALTER TABLE ${tr.identity(table)} DROP COLUMN ${tr.identity(this)}")
    }

    internal fun isOneColumnPK(): Boolean = this == table.primaryKey?.columns?.singleOrNull()

    /** Returns the SQL representation of this column. */
    @Suppress("ComplexMethod")
    fun descriptionDdl(modify: Boolean = false): String = buildString {
        val tr = TransactionManager.current()
        val column = this@Column
        append(tr.identity(column))
        append(" ")
        val isPKColumn = table.primaryKey?.columns?.contains(column) == true
        val isSQLiteAutoIncColumn = currentDialect is SQLiteDialect && columnType.isAutoInc

        when {
            !isPKColumn && isSQLiteAutoIncColumn -> tr.throwUnsupportedException("Auto-increment could be applied only to primary key column")
            isSQLiteAutoIncColumn && !isOneColumnPK() -> tr.throwUnsupportedException("Auto-increment could be applied only to a single column primary key")
            isSQLiteAutoIncColumn && table.isCustomPKNameDefined() -> {
                val rawType = columnType.sqlType().substringBefore("PRIMARY KEY")
                val constraintPart = table.primaryKeyConstraint()!!.substringBefore("(")
                append("$rawType $constraintPart AUTOINCREMENT")
            }

            else -> append(columnType.sqlType())
        }

        val defaultValue = dbDefaultValue
        if (defaultValue != null) {
            val expressionSQL = currentDialect.dataTypeProvider.processForDefaultValue(defaultValue)
            if (!currentDialect.isAllowedAsColumnDefault(defaultValue)) {
                val clientDefault = when {
                    defaultValueFun != null -> " Expression will be evaluated on the client."
                    !columnType.nullable -> " Column will be created with NULL marker."
                    else -> ""
                }
                exposedLogger.error("${currentDialect.name} ${tr.db.version} doesn't support expression '$expressionSQL' as default value.$clientDefault")
            } else {
                if (currentDialect is SQLServerDialect) {
                    // Create a DEFAULT constraint with an explicit name to facilitate removing it later if needed
                    val tableName = column.table.tableNameWithoutScheme
                    val columnName = column.name
                    val constraintName = "DF_${tableName}_$columnName"
                    append(" CONSTRAINT $constraintName DEFAULT $expressionSQL")
                } else {
                    append(" DEFAULT $expressionSQL")
                }
            }
        }

        if (extraDefinitions.isNotEmpty()) {
            append(extraDefinitions.joinToString(separator = " ", prefix = " ") { "$it" })
        }

        if (columnType.nullable || (defaultValue != null && defaultValueFun == null && !currentDialect.isAllowedAsColumnDefault(defaultValue))) {
            append(" NULL")
        } else if (!isPKColumn || (currentDialect is SQLiteDialect && !isSQLiteAutoIncColumn)) {
            append(" NOT NULL")
        }

        if (!modify && isOneColumnPK() && !isPrimaryConstraintWillBeDefined && !isSQLiteAutoIncColumn) {
            append(" PRIMARY KEY")
        }
    }

    internal fun <R> copyWithAnotherColumnType(columnType: ColumnType<R & Any>, body: (Column<R>.() -> Unit)? = null): Column<R> {
        val newColumn: Column<R> = Column(table, name, columnType)
        newColumn.foreignKey = foreignKey
        @Suppress("UNCHECKED_CAST")
        newColumn.dbDefaultValue = dbDefaultValue as Expression<R>?
        newColumn.isDatabaseGenerated = isDatabaseGenerated
        newColumn.extraDefinitions = extraDefinitions
        body?.let { newColumn.it() }

        if (defaultValueFun != null) {
            require(newColumn.defaultValueFun != null) { "defaultValueFun was lost on cloning the column" }
        }
        return newColumn
    }

    /**
     * Returns a copy of this column, but with the given column type.
     */
    fun withColumnType(columnType: IColumnType<T & Any>) = Column<T>(
        table = this.table,
        name = this.name,
        columnType = columnType
    ).also {
        it.foreignKey = this.foreignKey
        it.defaultValueFun = this.defaultValueFun
        it.dbDefaultValue = this.dbDefaultValue
        it.isDatabaseGenerated = this.isDatabaseGenerated
        it.extraDefinitions = this.extraDefinitions
    }

    override fun compareTo(other: Column<*>): Int = comparator.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Column<*>) return false

        if (table != other.table) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int = table.hashCode() * 31 + name.hashCode()

    override fun toString(): String = "${table.javaClass.name}.$name"
}
