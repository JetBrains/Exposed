package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.SQLiteDialect.Companion.ENABLE_UPDATE_DELETE_LIMIT
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun longAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun floatType(): String = "SINGLE"
    override fun binaryType(): String = "BLOB"
    override fun dateTimeType(): String = "TEXT"
    override fun timestampWithTimeZoneType(): String = "TEXT"
    override fun dateType(): String = "TEXT"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun jsonType(): String = "TEXT"
    override fun hexToDb(hexString: String): String = "X'$hexString'"
}

internal object SQLiteFunctionProvider : FunctionProvider() {
    override fun <T : String?> charLength(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("LENGTH(", expr, ")")
    }

    override fun <T : String?> substring(
        expr: Expression<T>,
        start: Expression<Int>,
        length: Expression<Int>,
        builder: QueryBuilder,
        prefix: String
    ): Unit = super.substring(expr, start, length, builder, "substr")

    override fun concat(separator: String, queryBuilder: QueryBuilder, vararg expr: Expression<*>) = queryBuilder {
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
            else -> super.groupConcat(expr, queryBuilder) // .replace(" SEPARATOR ", ", ")
        }
    }

    /**
     * Implementation of [FunctionProvider.locate]
     * Note: search is case-sensitive
     * */
    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("INSTR(", expr, ",\'", substring, "\')")
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
        append(")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%m',")
        append(expr)
        append(")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%d',")
        append(expr)
        append(")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%H',")
        append(expr)
        append(")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%M',")
        append(expr)
        append(")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STRFTIME('%S',")
        append(expr)
        append(")")
    }

    private const val UNSUPPORTED_AGGREGATE = "SQLite doesn't provide built-in aggregate function"

    override fun <T> stdDevPop(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("$UNSUPPORTED_AGGREGATE STDDEV_POP")

    override fun <T> stdDevSamp(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("$UNSUPPORTED_AGGREGATE STDDEV_SAMP")

    override fun <T> varPop(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("$UNSUPPORTED_AGGREGATE VAR_POP")

    override fun <T> varSamp(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("$UNSUPPORTED_AGGREGATE VAR_SAMP")

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType,
        queryBuilder: QueryBuilder
    ) = queryBuilder {
        append("JSON_EXTRACT(", expression, ", ")
        path.ifEmpty { arrayOf("") }.appendTo { +"'$$it'" }
        append(")")
    }

    override fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType,
        queryBuilder: QueryBuilder
    ) {
        val transaction = TransactionManager.current()
        if (path.size > 1) {
            transaction.throwUnsupportedException("SQLite does not support multiple JSON path arguments")
        }
        optional?.let {
            transaction.throwUnsupportedException("SQLite does not support optional arguments other than a path argument")
        }
        queryBuilder {
            append("JSON_TYPE(", expression, ", ")
            append("'$", path.firstOrNull() ?: "", "'")
            append(") IS NOT NULL")
        }
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
        if (!ENABLE_UPDATE_DELETE_LIMIT && limit != null) {
            transaction.throwUnsupportedException("SQLite doesn't support LIMIT in UPDATE clause.")
        }
        return super.update(target, columnsAndValues, limit, where, transaction)
    }

    override fun replace(
        table: Table,
        columns: List<Column<*>>,
        expression: String,
        transaction: Transaction,
        prepared: Boolean
    ): String {
        val insertStatement = super.insert(false, table, columns, expression, transaction)
        return insertStatement.replace("INSERT", "INSERT OR REPLACE")
    }

    override fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        where: Op<Boolean>?,
        transaction: Transaction,
        vararg keys: Column<*>
    ): String = with(QueryBuilder(true)) {
        appendInsertToUpsertClause(table, data, transaction)

        +" ON CONFLICT"
        val keyColumns = getKeyColumnsForUpsert(table, *keys) ?: emptyList()
        if (keyColumns.isNotEmpty()) {
            keyColumns.appendTo(prefix = " (", postfix = ")") { column ->
                append(transaction.identity(column))
            }
        }

        +" DO"
        val updateColumns = data.unzip().first.filter { it !in keyColumns }
        appendUpdateToUpsertClause(table, updateColumns, onUpdate, transaction, isAliasNeeded = false)

        where?.let {
            +" WHERE "
            +it
        }
        toString()
    }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (!ENABLE_UPDATE_DELETE_LIMIT && limit != null) {
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
    override val supportsCreateSchema: Boolean = false
    override val supportsWindowFrameGroupsMode: Boolean = true

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun createIndex(index: Index): String {
        if (index.indexType != null) {
            exposedLogger.warn(
                "Index of type ${index.indexType} on ${index.table.tableName} for ${index.columns.joinToString { it.name }} can't be created in SQLite"
            )
            return ""
        }
        val originalCreateIndex = super.createIndex(index.copy(unique = false))
        return if (index.unique) {
            originalCreateIndex.replace("CREATE INDEX", "CREATE UNIQUE INDEX")
        } else {
            originalCreateIndex
        }
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return "DROP INDEX IF EXISTS ${identifierManager.quoteIfNecessary(indexName)}"
    }

    override fun createDatabase(name: String) = "ATTACH DATABASE '${name.lowercase()}.db' AS ${name.inProperCase()}"

    override fun dropDatabase(name: String) = "DETACH DATABASE ${name.inProperCase()}"

    companion object : DialectNameProvider("sqlite") {
        val ENABLE_UPDATE_DELETE_LIMIT by lazy {
            var conn: Connection? = null
            var stmt: Statement? = null
            var rs: ResultSet? = null
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                conn = DriverManager.getConnection("jdbc:sqlite::memory:")
                stmt = conn!!.createStatement()
                rs = stmt!!.executeQuery("""SELECT sqlite_compileoption_used("ENABLE_UPDATE_DELETE_LIMIT");""")
                if (rs!!.next()) {
                    rs!!.getBoolean(1)
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            } finally {
                rs?.close()
                stmt?.close()
                conn?.close()
            }
        }
    }
}
