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
    /** Table where the columns is declared. */
    val table: Table,
    /** Name of the column. */
    val name: String,
    /** Data type of the column. */
    override val columnType: IColumnType
) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    var foreignKey: ForeignKeyConstraint? = null

    /** Returns the column that this column references. */
    val referee: Column<*>?
        get() = foreignKey?.target

    /** Returns the column that this column references, casted as a column of type [S], or `null` if the cast fails. */
    @Suppress("UNCHECKED_CAST")
    fun <S : T> referee(): Column<S>? = referee as? Column<S>

    /** Returns the index of this column in the primary key if there is a primary key, `null` otherwise. */
    var indexInPK: Int? = null
    /** Returns the function that calculates the default value for this column. */
    var defaultValueFun: (() -> T)? = null
    internal var dbDefaultValue: Expression<T>? = null

    /** Appends the SQL representation of this column to the specified [queryBuilder]. */
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = TransactionManager.current().fullIdentity(this@Column, queryBuilder)

    /** Returns the list of DDL statements that create this column. */
    val ddl: List<String> get() = createStatement()

    fun nameInDatabaseCase(): String = name.inProperCase()

    override fun createStatement(): List<String> {
        val alterTablePrefix = "ALTER TABLE ${TransactionManager.current().identity(table)} ADD"
        val isLastColumnInPK = table.primaryKey?.columns?.last() == this
        val columnDefinition = when {
            isOneColumnPK() && table.isCustomPKNameDefined() && isLastColumnInPK && currentDialect !is H2Dialect -> descriptionDdl() + ", ADD ${table.primaryKeyConstraint()}"
            isOneColumnPK() && (currentDialect is H2Dialect || currentDialect is SQLiteDialect) -> descriptionDdl().removeSuffix(" PRIMARY KEY")
            !isOneColumnPK() && isLastColumnInPK && currentDialect !is H2Dialect -> descriptionDdl() + ", ADD ${table.primaryKeyConstraint()}"
            else -> descriptionDdl()
        }

        val addConstr = if (isLastColumnInPK && currentDialect is H2Dialect) "$alterTablePrefix ${table.primaryKeyConstraint()}" else null
        return listOfNotNull("$alterTablePrefix $columnDefinition", addConstr)
    }

    override fun modifyStatement(): List<String> = listOf("ALTER TABLE ${TransactionManager.current().identity(table)} ${currentDialect.modifyColumn(this)}")

    override fun dropStatement(): List<String> {
        val tr = TransactionManager.current()
        return listOf("ALTER TABLE ${tr.identity(table)} DROP COLUMN ${tr.identity(this)}")
    }

    internal fun isOneColumnPK(): Boolean = table.primaryKey?.columns?.singleOrNull() == this

    /** Returns the SQL representation of this column. */
    fun descriptionDdl(): String = buildString {
        val tr = TransactionManager.current()
        append(tr.identity(this@Column))
        append(" ")
        val isPKColumn = table.primaryKey?.columns?.contains(this@Column) == true
        val colType = columnType
        val isSQLiteAutoIncColumn = currentDialect is SQLiteDialect && colType.isAutoInc

        when {
            !isPKColumn && isSQLiteAutoIncColumn -> tr.throwUnsupportedException("Auto-increment could be applied only to primary key column")
            isSQLiteAutoIncColumn && (!isOneColumnPK() || table.isCustomPKNameDefined()) && table.primaryKey != null -> append(currentDialect.dataTypeProvider.integerType())
            else -> append(colType.sqlType())
        }

        val defaultValue = dbDefaultValue
        if (defaultValue != null) {
            val expressionSQL = currentDialect.dataTypeProvider.processForDefaultValue(defaultValue)
            if (!currentDialect.isAllowedAsColumnDefault(defaultValue)) {
                val clientDefault = when {
                    defaultValueFun != null -> " Expression will be evaluated on the client."
                    !colType.nullable -> " Column will be created with NULL marker."
                    else -> ""
                }
                exposedLogger.error("${currentDialect.name} ${tr.db.version} doesn't support expression '$expressionSQL' as default value.$clientDefault")
            } else {
                append(" DEFAULT $expressionSQL")
            }
        }

        if (colType.nullable || (defaultValue != null && defaultValueFun == null && !currentDialect.isAllowedAsColumnDefault(defaultValue))) {
            append(" NULL")
        } else if (!isPKColumn) {
            append(" NOT NULL")
        }

        if (!table.isCustomPKNameDefined() && isOneColumnPK() && !isSQLiteAutoIncColumn) {
            append(" PRIMARY KEY")
        }
    }

    override fun compareTo(other: Column<*>): Int = comparator.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Column<*>) return false
        if (!super.equals(other)) return false

        if (table != other.table) return false
        if (name != other.name) return false
        if (columnType != other.columnType) return false

        return true
    }

    override fun hashCode(): Int = table.hashCode() * 31 + name.hashCode()

    override fun toString(): String = "${table.javaClass.name}.$name"
}
