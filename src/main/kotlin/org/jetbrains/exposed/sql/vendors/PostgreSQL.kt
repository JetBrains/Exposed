package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
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

internal object PostgreSQLFunctionProvider : FunctionProvider() {
    override fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        return super.update(targets, columnsAndValues, limit, where, transaction)
    }

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val sql = if (data.isEmpty()) ""
        else data.joinToString(prefix = "VALUES (", postfix = ")") { (col, value) ->
            builder.registerArgument(col, value)
        }

        val columns = data.map { it.first }

        val def = super.insert(false, table, columns, sql, transaction)

        val uniqueCols = columns.filter { it.indexInPK != null }.sortedBy { it.indexInPK }
        if (uniqueCols.isEmpty())
            transaction.throwUnsupportedException("Postgres replace table must supply at least one primary key")
        val conflictKey = uniqueCols.joinToString { transaction.identity(it) }
        return def + "ON CONFLICT ($conflictKey) DO UPDATE SET " + columns.joinToString { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) "$def $onConflictIgnore" else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("LIMIT is not supported in DELETE in PostgreSQL")
        return super.delete(ignore, table, where, limit, transaction)
    }

    private const val onConflictIgnore = "ON CONFLICT DO NOTHING"

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder): String {
        val tr = TransactionManager.current()
        return when {
            expr.orderBy.isNotEmpty() -> tr.throwUnsupportedException("PostgreSQL doesn't support ORDER BY in STRING_AGG.")
            expr.distinct -> tr.throwUnsupportedException("PostgreSQL doesn't support DISTINCT in STRING_AGG.")
            expr.separator == null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG.")
            else -> "STRING_AGG(${expr.expr.toSQL(queryBuilder)}, '${expr.separator}')"
        }
    }
}

open class PostgreSQLDialect : VendorDialect(dialectName, PostgreSQLDataTypeProvider, PostgreSQLFunctionProvider) {
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

    companion object {
        const val dialectName = "postgresql"
    }
}