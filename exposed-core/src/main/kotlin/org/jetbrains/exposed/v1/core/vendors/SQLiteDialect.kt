package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.exceptions.throwUnsupportedException

internal object SQLiteDataTypeProvider : DataTypeProvider() {
    override fun integerAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun longAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun ulongAutoincType(): String = "INTEGER PRIMARY KEY AUTOINCREMENT"
    override fun floatType(): String = "SINGLE"
    override fun binaryType(): String = "BLOB"
    override fun dateTimeType(): String = "TEXT"
    override fun timestampWithTimeZoneType(): String = "TEXT"
    override fun dateType(): String = "TEXT"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun jsonType(): String = "TEXT"

    override fun jsonBType(): String {
        @Suppress("MagicNumber")
        @OptIn(InternalApi::class)
        val supportedVersion = Version(3, 45, 0)

        @OptIn(InternalApi::class)
        val currentVersion = Version.from(currentTransaction().db.fullVersion.trim())

        return if (currentVersion.covers(supportedVersion)) blobType() else super.jsonBType()
    }

    override fun hexToDb(hexString: String): String = "X'$hexString'"
}

@Suppress("TooManyFunctions")
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
            expr.asList().appendTo(this, separator = " || ") { +it }
        } else {
            expr.asList().appendTo(this, separator = " || '$separator' || ") { +it }
        }
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        @OptIn(InternalApi::class)
        if (expr.distinct) {
            currentTransaction().throwUnsupportedException("SQLite doesn't support DISTINCT in GROUP_CONCAT function")
        }
        queryBuilder {
            +"GROUP_CONCAT("
            +expr.expr
            expr.separator?.let {
                +", '$it'"
            }
            if (expr.orderBy.isNotEmpty()) {
                expr.orderBy.appendTo(prefix = " ORDER BY ") { (expression, sortOrder) ->
                    currentDialect.dataTypeProvider.precessOrderByClause(this, expression, sortOrder)
                }
            }
            +")"
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
    ) {
        @OptIn(InternalApi::class)
        currentTransaction().throwUnsupportedException("SQLite doesn't provide built in REGEXP expression, use LIKE instead.")
    }

    override fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append(
            "SUBSTR(", expr, ", INSTR(", expr, ", ' ') + 1,\n",
            "CASE\n",
            "    WHEN INSTR(", expr, ", 'Z') > 0 THEN\n",
            "        INSTR(", expr, ", 'Z') - 1\n",
            "    WHEN INSTR(", expr, ", '+') > 0 THEN\n",
            "        INSTR(", expr, ", '+') - 1\n",
            "    WHEN INSTR(", expr, ", '-') > 0 THEN\n",
            "        INSTR(", expr, ", '-') - 1\n",
            "    ELSE\n",
            "        LENGTH(", expr, ")\n",
            "END- INSTR(", expr, ", ' '))"
        )
    }

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
    ) {
        @OptIn(InternalApi::class)
        currentTransaction().throwUnsupportedException("$UNSUPPORTED_AGGREGATE STDDEV_POP")
    }

    override fun <T> stdDevSamp(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        currentTransaction().throwUnsupportedException("$UNSUPPORTED_AGGREGATE STDDEV_SAMP")
    }

    override fun <T> varPop(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        currentTransaction().throwUnsupportedException("$UNSUPPORTED_AGGREGATE VAR_POP")
    }

    override fun <T> varSamp(
        expression: Expression<T>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        currentTransaction().throwUnsupportedException("$UNSUPPORTED_AGGREGATE VAR_SAMP")
    }

    override fun <T> jsonCast(expression: Expression<T>, jsonType: IColumnType<*>, queryBuilder: QueryBuilder) {
        queryBuilder {
            append("JSON(", expression, ')')
        }
    }

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType<*>,
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
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        val transaction = currentTransaction()
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
        expression: String,
        onUpdate: List<Pair<Column<*>, Any?>>,
        keyColumns: List<Column<*>>,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        +insert(false, table, data.unzip().first, expression, transaction)

        +" ON CONFLICT"
        if (keyColumns.isNotEmpty()) {
            keyColumns.appendTo(prefix = " (", postfix = ")") { column ->
                append(transaction.identity(column))
            }
        }

        +" DO UPDATE SET "
        onUpdate.appendTo { (columnToUpdate, updateExpression) ->
            append("${transaction.identity(columnToUpdate)}=")
            registerArgument(columnToUpdate, updateExpression)
        }

        where?.let {
            +" WHERE "
            +it
        }
        toString()
    }

    override fun insertValue(columnName: String, queryBuilder: QueryBuilder) {
        queryBuilder { +"EXCLUDED.$columnName" }
    }

    override fun queryLimitAndOffset(size: Int?, offset: Long, alreadyOrdered: Boolean): String {
        @OptIn(InternalApi::class)
        if (size == null && offset > 0) {
            currentTransaction().throwUnsupportedException("SQLite doesn't support OFFSET clause without LIMIT")
        }
        return super.queryLimitAndOffset(size, offset, alreadyOrdered)
    }

    override fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        if (analyze || options != null) {
            transaction.throwUnsupportedException("SQLite does not support ANALYZE or other options in EXPLAIN queries.")
        }
        val sql = super.explain(false, null, internalStatement, transaction)
        return sql.replaceFirst("EXPLAIN ", "EXPLAIN QUERY PLAN ")
    }

    override fun returning(
        mainSql: String,
        returning: List<Expression<*>>,
        transaction: Transaction
    ): String {
        return with(QueryBuilder(true)) {
            +"$mainSql RETURNING "
            returning.appendTo { +it }
            toString()
        }
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
        return "DROP INDEX IF EXISTS ${identifierManager.cutIfNecessaryAndQuote(indexName)}"
    }

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        exposedLogger.warn("Modifying an existing column definition with ALTER TABLE is not supported in SQLite")
        return emptyList()
    }

    override fun modifyColumn(originalColumnData: ColumnMetadata, column: Column<*>, columnDiff: ColumnDiff): List<String> {
        if (columnDiff.hasDifferences() && !columnDiff.caseSensitiveName) {
            exposedLogger.warn("ALTER TABLE only supports modifying the column name in SQLite")
            return emptyList()
        }

        @OptIn(InternalApi::class)
        val tr = currentTransaction()
        return listOf("ALTER TABLE ${tr.identity(column.table)} RENAME COLUMN ${originalColumnData.name} TO ${tr.identity(column)}")
    }

    override fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String {
        exposedLogger.warn("ALTER TABLE does not support adding a primary key to an existing table in SQLite")
        return ""
    }

    override fun createDatabase(name: String): String {
        @OptIn(InternalApi::class)
        return "ATTACH DATABASE '${name.lowercase()}.db' AS ${name.inProperCase()}"
    }

    override fun listDatabases(): String = "SELECT name FROM pragma_database_list"

    override fun dropDatabase(name: String): String {
        @OptIn(InternalApi::class)
        return "DETACH DATABASE ${name.inProperCase()}"
    }

    companion object : DialectNameProvider("SQLite")
}
