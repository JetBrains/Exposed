package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
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

    val ddl: String
        get() = createStatement()

    override fun createStatement(): String = "ALTER TABLE ${TransactionManager.current().identity(table)} ADD COLUMN ${descriptionDdl()}"

    override fun modifyStatement(): String = "ALTER TABLE ${TransactionManager.current().identity(table)} MODIFY COLUMN ${descriptionDdl()}"

    override fun dropStatement(): String = TransactionManager.current().let {"ALTER TABLE ${it.identity(table)} DROP COLUMN ${it.identity(this)}" }

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