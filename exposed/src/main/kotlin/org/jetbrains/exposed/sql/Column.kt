package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable

private val comparator = compareBy<Column<*>>({ it.table.tableName }, { it.name })

class Column<T>(val table: Table, val name: String, override val columnType: IColumnType) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    var referee: Column<*>? = null
    fun <S:T> referee() : Column<S>? = referee as? Column<S>
    internal var onUpdate: ReferenceOption? = null
        get() = field ?: currentDialectIfAvailable?.defaultReferenceOption
    internal var onDelete: ReferenceOption? = null
                get() = field ?: currentDialectIfAvailable?.defaultReferenceOption
    internal var indexInPK: Int? = null
    internal var defaultValueFun: (() -> T)? = null
    internal var dbDefaultValue: Expression<T>? = null

    override fun equals(other: Any?): Boolean {
        return (other as? Column<*>)?.let {
            it.table == table && it.name == name && it.columnType == columnType
        } ?: false
    }

    override fun hashCode(): Int = table.hashCode()*31 + name.hashCode()

    override fun toString(): String = "${table.javaClass.name}.$name"

    override fun toSQL(queryBuilder: QueryBuilder): String = TransactionManager.current().fullIdentity(this)

    val ddl: List<String>
        get() = createStatement()

    override fun createStatement(): List<String> {
        val alterTablePrefix = "ALTER TABLE ${TransactionManager.current().identity(table)} ADD"
        val isLastColumnInPK = indexInPK != null && indexInPK == table.columns.mapNotNull { it.indexInPK }.max()
        val columnDefinition = when {
            isOneColumnPK() && (currentDialect is H2Dialect || currentDialect is SQLiteDialect) -> descriptionDdl().removeSuffix(" PRIMARY KEY")
            !isOneColumnPK() && isLastColumnInPK && currentDialect !is H2Dialect -> descriptionDdl() + ", ADD ${table.primaryKeyConstraint()}"
            else -> descriptionDdl()
        }

        val addConstr = if (isLastColumnInPK && currentDialect is H2Dialect) {
             "$alterTablePrefix ${table.primaryKeyConstraint()}"
        } else null
        return listOfNotNull("$alterTablePrefix $columnDefinition", addConstr)
    }

    override fun modifyStatement() = listOf("ALTER TABLE ${TransactionManager.current().identity(table)} ${currentDialect.modifyColumn(this)}")

    override fun dropStatement() = listOf(TransactionManager.current().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" })

    internal fun isOneColumnPK() = table.columns.singleOrNull { it.indexInPK != null } == this

    fun descriptionDdl(): String = buildString {
        val tr = TransactionManager.current()
        append(tr.identity(this@Column))
        append(" ")
        val isPKColumn = indexInPK != null
        val colType = columnType
        if (currentDialect is SQLiteDialect && colType.isAutoInc && table.columns.any{ it.indexInPK != null}) {
            append(colType.sqlType().removeSuffix(" AUTOINCREMENT")) // Workaround as SQLite Doesn't support both PK and autoInc in DDL
        } else {
            append(colType.sqlType())
        }

        val _dbDefaultValue = dbDefaultValue
        if (!isPKColumn && _dbDefaultValue != null) {
            val expressionSQL = currentDialect.dataTypeProvider.processForDefaultValue(_dbDefaultValue)
            if (!currentDialect.isAllowedAsColumnDefault(_dbDefaultValue)) {
                val clientDefault = when {
                    defaultValueFun != null -> " Expression will be evaluated on client."
                    !colType.nullable -> " Column will be created with NULL marker."
                    else -> ""
                }
                exposedLogger.error("${currentDialect.name} ${tr.db.version} doesn't support expression '$expressionSQL' as default value.$clientDefault")
            } else {
                append(" DEFAULT $expressionSQL" )
            }
        }

        if (colType.nullable || (_dbDefaultValue != null && defaultValueFun == null && !currentDialect.isAllowedAsColumnDefault(_dbDefaultValue))) {
            append(" NULL")
        } else if (!isPKColumn) {
            append(" NOT NULL")
        }

        if (isOneColumnPK()) {
            append(" PRIMARY KEY")
        }
    }

    override fun compareTo(other: Column<*>): Int = comparator.compare(this, other)
}