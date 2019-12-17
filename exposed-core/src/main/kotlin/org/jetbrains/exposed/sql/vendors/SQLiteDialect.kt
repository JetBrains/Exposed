package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun longAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun floatType(): String = "SINGLE"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun dateTimeType(): String  = "NUMERIC"
    override val blobAsStream: Boolean = true

    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override fun<T:String?> substring(expr: Expression<T>, start: Expression<Int>, length: Expression<Int>, builder: QueryBuilder, prefix: String) =
            super.substring(expr, start, length, builder, "substr")

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) def.replaceFirst("INSERT", "INSERT OR IGNORE") else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("LIMIT is not supported in DELETE in SQLite")
        val def = super.delete(false, table, where, limit, transaction)
        return if (ignore) def.replaceFirst("DELETE", "DELETE OR IGNORE") else def
    }

    override fun update(targets: ColumnSet, columnsAndValues: List<Pair<Column<*>, Any?>>, limit: Int?, where: Op<Boolean>?, transaction: Transaction): String {
        if (limit != null) transaction.throwUnsupportedException("SQLite doesn't support LIMIT in UPDATE clause.")
        return super.update(targets, columnsAndValues, limit, where, transaction)
    }

    override fun <T: String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.orderBy.isNotEmpty() -> tr.throwUnsupportedException("SQLite doesn't support ORDER BY in GROUP_CONCAT.")
            expr.distinct ->  tr.throwUnsupportedException("SQLite doesn't support DISTINCT in GROUP_CONCAT.")
            else -> super.groupConcat(expr, queryBuilder)//.replace(" SEPARATOR ", ", ")
        }

    }

    override fun <T : String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder) {
        TransactionManager.current().throwUnsupportedException("SQLite doesn't provide built in REGEXP expression")
    }

    override fun <T : String?> concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<T>) = queryBuilder {
        if (separator == "")
            expr.toList().appendTo(this, separator = " || ") { +it }
        else
            expr.toList().appendTo(this, separator = " || '$separator' || ") { +it }
    }

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%Y',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%m',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%d',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%H',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%M',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("STRFTIME('%S',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }
}

open class SQLiteDialect : VendorDialect(dialectName, SQLiteDataTypeProvider, SQLiteFunctionProvider) {
    override val supportsMultipleGeneratedKeys: Boolean = false
    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        val originalCreateIndex = super.createIndex(index.copy(unique = false))
        return if (index.unique) originalCreateIndex.replace("CREATE INDEX", "CREATE UNIQUE INDEX")
        else originalCreateIndex
    }

    companion object {
        const val dialectName = "sqlite"
    }
}
