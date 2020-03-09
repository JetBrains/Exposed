package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun longAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun floatType(): String = "SINGLE"
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override val blobAsStream: Boolean = true
    override fun dateTimeType(): String = "NUMERIC"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override fun <T : String?> substring(
        expr: Expression<T>,
        start: Expression<Int>,
        length: Expression<Int>,
        builder: QueryBuilder,
        prefix: String
    ): Unit = super.substring(expr, start, length, builder, "substr")

    override fun <T : String?> concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<T>) = queryBuilder {
        if (separator == "") {
            expr.toList().appendTo(this, separator = " || ") { +it }
        } else {
            expr.toList().appendTo(this, separator = " || '$separator' || ") { +it }
        }
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.orderBy.isNotEmpty() -> tr.throwUnsupportedException("SQLite doesn't support ORDER BY in GROUP_CONCAT function.")
            expr.distinct -> tr.throwUnsupportedException("SQLite doesn't support DISTINCT in GROUP_CONCAT function.")
            else -> super.groupConcat(expr, queryBuilder)//.replace(" SEPARATOR ", ", ")
        }
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("SQLite doesn't provide built in REGEXP expression, use LIKE instead.")

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%Y',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%m',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%d',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%H',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%M',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%S',")
        append(expr)
        append(" / 1000, 'unixepoch')")
    }

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) def.replaceFirst("INSERT", "INSERT OR IGNORE") else def
    }

    override fun update(
        target: Table,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("SQLite doesn't support LIMIT in UPDATE clause.")
        }
        return super.update(target, columnsAndValues, limit, where, transaction)
    }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("SQLite doesn't support LIMIT in DELETE clause.")
        }
        val def = super.delete(false, table, where, limit, transaction)
        return if (ignore) def.replaceFirst("DELETE", "DELETE OR IGNORE") else def
    }
}

/**
 * SQLite dialect implementation.
 */
open class SQLiteDialect : VendorDialect(dialectName, SQLiteDataTypeProvider, SQLiteFunctionProvider) {
    override val supportsCreateSequence: Boolean = false
    override val supportsMultipleGeneratedKeys: Boolean = false

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        val originalCreateIndex = super.createIndex(index.copy(unique = false))
        return if (index.unique) {
            originalCreateIndex.replace("CREATE INDEX", "CREATE UNIQUE INDEX")
        } else {
            originalCreateIndex
        }
    }

    override fun createDatabase(name: String) = "ATTACH DATABASE '${name.toLowerCase()}.db' AS ${name.inProperCase()}"

    override fun dropDatabase(name: String) = "DETACH DATABASE ${name.inProperCase()}"

    companion object {
        /** SQLite dialect name */
        const val dialectName: String = "sqlite"
    }
}
