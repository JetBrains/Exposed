package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.inProperCase
import kotlin.comparisons.compareBy

open class Column<T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    var referee: Column<*>? = null
    internal var indexInPK: Int? = null
    internal var onDelete: ReferenceOption? = null
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
        val isLastColumnInPK = indexInPK != null && indexInPK == table.columns.mapNotNull { indexInPK }.max()
        val columnDefinition = when {
            isOneColumnPK() && currentDialect in listOf(H2Dialect, SQLiteDialect) -> descriptionDdl().removeSuffix(" PRIMARY KEY")
            !isOneColumnPK() && isLastColumnInPK && currentDialect != H2Dialect-> ", ADD ${table.primaryKeyConstraint()}"
            else -> descriptionDdl()
        }

        val addConstr = if (isLastColumnInPK && currentDialect == H2Dialect) {
             "$alterTablePrefix ${table.primaryKeyConstraint()}"
        } else null
        return listOfNotNull("$alterTablePrefix COLUMN $columnDefinition", addConstr)
    }

    override fun modifyStatement() = listOf("ALTER TABLE ${TransactionManager.current().identity(table)} MODIFY COLUMN ${descriptionDdl()}")

    override fun dropStatement() = listOf(TransactionManager.current().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" })

    internal fun isOneColumnPK() = table.columns.filter { it.indexInPK != null }.singleOrNull() == this

    fun descriptionDdl(): String = buildString {
        append(TransactionManager.current().identity(this@Column).inProperCase())
        append(" ")
        val isPKColumn = indexInPK != null
        val colType = columnType
        if (currentDialect == SQLiteDialect && isOneColumnPK() && colType.autoinc) {
            append(colType.sqlType().removeSuffix(" AUTO_INCREMENT")) // Workaround as SQLite Doesn't support both PK and autoInc in DDL
        } else {
            append(colType.sqlType())
        }

        if (colType.nullable || (dbDefaultValue != null && defaultValueFun == null && !currentDialect.supportsExpressionsAsDefault)) {
            append(" NULL")
        } else if (!isPKColumn) {
            append(" NOT NULL")
        }

        if (!isPKColumn && dbDefaultValue != null) {
            if (defaultValueFun == null && !currentDialect.supportsExpressionsAsDefault) {
                exposedLogger.error("${currentDialect.name} doesn't support expressions as default value. Only constants allowed.")
            } else {
                append(" DEFAULT ")
                append(dbDefaultValue!!.toSQL(QueryBuilder(false)))
            }
        }

        if (isOneColumnPK()) {
            append(" PRIMARY KEY")
        }
    }

    override fun compareTo(other: Column<*>): Int = compareBy<Column<*>>({it.table.tableName}, {it.name}).compare(this, other)
}