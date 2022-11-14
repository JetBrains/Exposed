package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import java.util.*

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
    override val columnType: IColumnType
) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    var foreignKey: ForeignKeyConstraint? = null

    /** Returns the column that this column references. */
    val referee: Column<*>?
        get() = foreignKey?.targetOf(this)

    /** Returns the column that this column references, casted as a column of type [S], or `null` if the cast fails. */
    @Suppress("UNCHECKED_CAST")
    fun <S : T> referee(): Column<S>? = referee as? Column<S>

    /** Returns the function that calculates the default value for this column. */
    var defaultValueFun: (() -> T)? = null
    internal var dbDefaultValue: Expression<T>? = null

    /** Appends the SQL representation of this column to the specified [queryBuilder]. */
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = TransactionManager.current().fullIdentity(this@Column, queryBuilder)

    /** Returns the list of DDL statements that create this column. */
    val ddl: List<String> get() = createStatement()

    fun nameInDatabaseCase(): String = name.inProperCase()

    private val isLastColumnInPK: Boolean get() = table.primaryKey?.columns?.last() == this

    internal val isPrimaryConstraintWillBeDefined: Boolean get() = when {
        currentDialect is SQLiteDialect && columnType.isAutoInc -> false
        table.isCustomPKNameDefined() -> isLastColumnInPK
        isOneColumnPK() -> false
        else -> isLastColumnInPK
    }

    override fun createStatement(): List<String> {
        val alterTablePrefix = "ALTER TABLE ${TransactionManager.current().identity(table)} ADD"
        val isH2withCustomPKConstraint = currentDialect is H2Dialect && isLastColumnInPK
        val columnDefinition = when {
            isPrimaryConstraintWillBeDefined && isLastColumnInPK && !isH2withCustomPKConstraint ->
                descriptionDdl(false) + ", ADD ${table.primaryKeyConstraint()}"
            isH2withCustomPKConstraint -> descriptionDdl(true)
            else -> descriptionDdl(false)
        }

        val addConstr = if (isH2withCustomPKConstraint) "$alterTablePrefix ${table.primaryKeyConstraint()}" else null
        return listOfNotNull("$alterTablePrefix $columnDefinition", addConstr)
    }

    fun modifyStatements(columnDiff: ColumnDiff): List<String> = currentDialect.modifyColumn(this, columnDiff)

    override fun modifyStatement(): List<String> = currentDialect.modifyColumn(this, ColumnDiff.AllChanged)

    override fun dropStatement(): List<String> {
        val tr = TransactionManager.current()
        return listOf("ALTER TABLE ${tr.identity(table)} DROP COLUMN ${tr.identity(this)}")
    }

    internal fun isOneColumnPK(): Boolean = table.primaryKey?.columns?.singleOrNull() == this

    /** Returns the SQL representation of this column. */
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
                append(" DEFAULT $expressionSQL")
            }
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

    /**
     * Returns a copy of this column, but with the given column type.
     */
    fun withColumnType(columnType: IColumnType) = Column<T>(
        table = this.table,
        name = this.name,
        columnType = columnType
    ).also {
        it.foreignKey = this.foreignKey
        it.defaultValueFun = this.defaultValueFun
        it.dbDefaultValue = this.dbDefaultValue
    }

    override fun compareTo(other: Column<*>): Int = comparator.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Column<*>) return false

        if (table != other.table) return false
        if (name != other.name) return false
        if (columnType != other.columnType) return false

        return true
    }

    override fun hashCode(): Int = table.hashCode() * 31 + name.hashCode()

    override fun toString(): String = "${table.javaClass.name}.$name"
}
