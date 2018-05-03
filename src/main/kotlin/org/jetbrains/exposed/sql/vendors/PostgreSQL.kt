package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {

    override fun shortAutoincType(): String = "SERIAL"

    override fun longAutoincType(): String = "BIGSERIAL"

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun uuidType(): String = "uuid"

    override fun blobType(): String = "bytea"

    override fun binaryType(length: Int): String = "bytea"

    override fun uuidToDB(value: UUID): Any = value

    override val blobAsStream: Boolean = true
}

internal class PostgreSQLDialect : VendorDialect(dialectName, PostgreSQLDataTypeProvider) {
    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>): String = buildString {
        val colName = TransactionManager.current().identity(column)
        append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()},")
        append("ALTER COLUMN $colName ")
        if (column.columnType.nullable)
            append("DROP ")
        else
            append("SET ")
        append("NOT NULL")
        column.dbDefaultValue?.let {
            append(", ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
        }
    }

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {

        val builder = QueryBuilder(true)
        val sql = if (data.isEmpty()) ""
        else data.joinToString(prefix = "VALUES (", postfix = ")") { (col, value) ->
            builder.registerArgument(col, value)
        }

        val columns = data.map { it.first }

        val def = super.insert(false, table, columns, sql, transaction)

        val uniqueIdxCols = table.indices.filter { it.unique }.flatMap { it.columns.toList() }
        val uniqueCols = columns.filter { it.indexInPK != null || it in uniqueIdxCols }

        val conflictKey = uniqueCols.joinToString { transaction.identity(it) }
        return def + "ON CONFLICT ($conflictKey) DO UPDATE SET " + columns.joinToString { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) "$def $onConflictIgnore" else def
    }

    companion object {
        const val dialectName = "postgresql"
        private const val onConflictIgnore = "ON CONFLICT DO NOTHING"
    }
}