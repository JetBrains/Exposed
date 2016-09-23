package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import kotlin.comparisons.compareBy

open class Column<T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T>(), DdlAware, Comparable<Column<*>> {
    var referee: Column<*>? = null
    internal var indexInPK: Int? = null
    internal var onDelete: ReferenceOption? = null
    internal var defaultValueFun: (() -> T)? = null
    internal var dbDefaultValue: T? = null

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
        val (pkDDL, addConstr) = if (indexInPK != null && indexInPK == table.columns.mapNotNull { indexInPK }.max()) {
                if (currentDialect != H2Dialect) {
                    ", ADD ${table.primaryKeyConstraint()}" to null
                } else {
                    "" to "$alterTablePrefix ${table.primaryKeyConstraint()}"
                }
        } else {
            "" to null
        }
        return listOfNotNull("$alterTablePrefix COLUMN ${descriptionDdl()}$pkDDL", addConstr)
    }

    override fun modifyStatement() = listOf("ALTER TABLE ${TransactionManager.current().identity(table)} MODIFY COLUMN ${descriptionDdl()}")

    override fun dropStatement() = listOf(TransactionManager.current().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" })

    fun descriptionDdl(): String = buildString {
        append(TransactionManager.current().identity(this@Column))
        append(" ")
        val colType = columnType
        append(colType.sqlType())

        if (colType.nullable) {
            append(" NULL")
        } else {
            append(" NOT NULL")
        }

        if (dbDefaultValue != null) {
            append (" DEFAULT ")
            append(colType.valueToString(dbDefaultValue!!))
        }
    }

    override fun compareTo(other: Column<*>): Int = compareBy<Column<*>>({it.table.tableName}, {it.name}).compare(this, other)
}