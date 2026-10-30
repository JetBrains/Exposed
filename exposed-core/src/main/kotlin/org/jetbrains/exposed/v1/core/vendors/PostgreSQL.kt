package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.functions.vector.VectorDistanceMetric
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.exceptions.throwUnsupportedException
import java.util.UUID as JavaUUID

internal object PostgreSQLDataTypeProvider : DataTypeProvider() {
    override fun byteType(): String = "SMALLINT"
    override fun floatType(): String = "REAL"
    override fun ulongType(): String = "BIGINT"
    override fun integerAutoincType(): String = "SERIAL"
    override fun longAutoincType(): String = "BIGSERIAL"
    override fun ulongAutoincType(): String = "BIGSERIAL"
    override fun uuidType(): String = "uuid"
    override fun binaryType(): String = "bytea"
    override fun binaryType(length: Int): String {
        exposedLogger.warn("The length of the binary column is not required.")
        return binaryType()
    }

    override fun blobType(): String = "bytea"
    override fun uuidToDB(value: JavaUUID): Any = value
    override fun dateTimeType(): String = "TIMESTAMP"
    override fun jsonBType(): String = "JSONB"

    override fun processForDefaultValue(e: Expression<*>): String = when (e) {
        is LiteralOp<*> if (e.columnType is JsonColumnMarker && (currentDialect as? H2Dialect) == null) -> {
            val cast = if (e.columnType.usesBinaryFormat) "::jsonb" else "::json"
            "${super.processForDefaultValue(e)}$cast"
        }
        is LiteralOp<*> if (
            e.columnType is BlobColumnType && e.columnType.useObjectIdentifier && (currentDialect as? H2Dialect) == null
            ) -> {
            "lo_from_bytea(0, ${super.processForDefaultValue(e)} :: bytea)"
        }
        is LiteralOp<*> if e.columnType is ArrayColumnType<*, *> -> {
            val processed = super.processForDefaultValue(e)
            processed
                .takeUnless { it == "ARRAY[]" }
                ?: run {
                    val cast = e.columnType.delegateType.lowercase()
                    "$processed::$cast[]"
                }
        }
        else -> super.processForDefaultValue(e)
    }

    override fun hexToDb(hexString: String): String = """E'\\x$hexString'"""
}

internal object PostgreSQLFunctionProvider : FunctionProvider() {

    override fun nextVal(seq: Sequence, builder: QueryBuilder): Unit = builder {
        append("NEXTVAL('", seq.identifier, "')")
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        @OptIn(InternalApi::class)
        val tr = currentTransaction()
        return when (expr.separator) {
            null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG function.")
            else -> queryBuilder {
                append("STRING_AGG(")
                if (expr.distinct) append(" DISTINCT ")
                append(expr.expr, ", ")
                appendStringArgument(expr.separator)
                if (expr.orderBy.isNotEmpty()) {
                    expr.orderBy.appendTo(prefix = " ORDER BY ") {
                        append(it.first, " ", it.second.name)
                    }
                }
                append(")")
            }
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
        append("POSITION(")
        appendStringArgument(substring)
        append(" IN ", expr, ")")
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        append(expr1)
        if (caseSensitive) {
            append(" ~ ")
        } else {
            append(" ~* ")
        }
        append(pattern)
    }

    override fun <T> date(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("CAST(", expr, " AS DATE)")
    }

    override fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("TO_CHAR(", expr, ", 'HH24:MI:SS.US')")
    }

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(YEAR FROM ")
        append(expr)
        append(")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(MONTH FROM ")
        append(expr)
        append(")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(DAY FROM ")
        append(expr)
        append(")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(HOUR FROM ")
        append(expr)
        append(")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(MINUTE FROM ")
        append(expr)
        append(")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("Extract(SECOND FROM ")
        append(expr)
        append(")")
    }

    override fun <T> arraySlice(expression: Expression<T>, lower: Int?, upper: Int?, queryBuilder: QueryBuilder) {
        queryBuilder {
            append(expression, "[")
            lower?.let { +it.toString() }
            +":"
            upper?.let { +it.toString() }
            +"]"
        }
    }

    override fun <T> vectorDistance(
        expression: Expression<T>,
        targetExpression: Expression<T>,
        metric: VectorDistanceMetric,
        queryBuilder: QueryBuilder
    ) {
        queryBuilder {
            append(expression)
            when (metric) {
                VectorDistanceMetric.COSINE -> +" <=> "
                VectorDistanceMetric.EUCLIDEAN -> +" <-> "
                VectorDistanceMetric.DOT -> +" <#> "
            }
            append(targetExpression)
        }
    }

    override fun <T> jsonCast(expression: Expression<T>, jsonType: IColumnType<*>, queryBuilder: QueryBuilder) {
        queryBuilder {
            append(expression, "::json")
        }
    }

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) = queryBuilder {
        append("${jsonType.sqlType()}_EXTRACT_PATH")
        if (toScalar) append("_TEXT")
        append("(", expression, ", ")
        path.ifEmpty { arrayOf("$") }.appendTo { appendStringArgument(it) }
        append(")")
    }

    override fun jsonContains(
        target: Expression<*>,
        candidate: Expression<*>,
        path: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        path?.let {
            currentTransaction().throwUnsupportedException("PostgreSQL does not support a JSON path argument")
        }
        val isNotJsonB = !(jsonType as JsonColumnMarker).usesBinaryFormat
        val candidateNeedsCast = candidate !is QueryParameter || candidate.columnType is StringColumnType
        queryBuilder {
            append(target)
            if (isNotJsonB) append("::jsonb")
            append(" @> ", candidate)
            if (isNotJsonB || candidateNeedsCast) append("::jsonb")
        }
    }

    override fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        @OptIn(InternalApi::class)
        if (path.size > 1) {
            currentTransaction().throwUnsupportedException("PostgreSQL does not support multiple JSON path arguments")
        }
        val isNotJsonB = !(jsonType as JsonColumnMarker).usesBinaryFormat
        queryBuilder {
            append("JSONB_PATH_EXISTS(")
            if (isNotJsonB) {
                append("CAST(", expression, " as jsonb), ")
            } else {
                append(expression, ", ")
            }
            appendCastStringArgument("\$" + (path.firstOrNull() ?: ""), "jsonpath")
            optional?.let {
                append(", ")
                appendCastStringArgument(it, "jsonb")
            }
            append(")")
        }
    }

    /**
     * Binds [value] as a parameter cast to [type], since a bound parameter is not resolved to the type that the
     * function expects the way an untyped string literal is.
     *
     * pgjdbc-ng cannot send a parameter that PostgreSQL infers as `jsonpath` or `jsonb`, so on that driver the
     * parameter is first pinned to `text`.
     */
    private fun QueryBuilder.appendCastStringArgument(value: String, type: String) {
        if (currentDialect is PostgreSQLNGDialect) {
            append("CAST(")
            appendStringArgument(value)
            append(" AS text)")
        } else {
            appendStringArgument(value)
        }
        append("::", type)
    }

    private const val ON_CONFLICT_IGNORE = "ON CONFLICT DO NOTHING"

    override fun insert(
        ignore: Boolean,
        table: Table,
        columns: List<Column<*>>,
        expr: String,
        transaction: Transaction
    ): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) "$def $ON_CONFLICT_IGNORE" else def
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        }
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
            ?: transaction.throwUnsupportedException("PostgreSQL supports a join updates with a single table columns to update.")
        targets.checkJoinTypes(StatementType.UPDATE)
        +"UPDATE "
        tableToUpdate.describe(transaction, this)
        +" SET "
        columnsAndValues.appendTo(this) { (col, value) ->
            append("${transaction.identity(col)}=")
            registerArgument(col, value)
        }

        appendJoinPartForUpdateClause(tableToUpdate, targets, transaction)

        where?.let {
            +" AND "
            +it
        }
        toString()
    }

    override fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        expression: String,
        onUpdate: List<Pair<Column<*>, Any?>>,
        keyColumns: List<Column<*>>,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (keyColumns.isEmpty()) {
            transaction.throwUnsupportedException("UPSERT requires a unique key or constraint as a conflict target")
        }

        return with(QueryBuilder(true)) {
            +insert(false, table, data.unzip().first, expression, transaction)

            +" ON CONFLICT "
            keyColumns.appendTo(prefix = "(", postfix = ")") { column ->
                append(transaction.identity(column))
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
    }

    override fun insertValue(columnName: String, queryBuilder: QueryBuilder) {
        queryBuilder { +"EXCLUDED.$columnName" }
    }

    override fun delete(
        ignore: Boolean,
        targets: Join,
        targetTables: List<Table>,
        where: Op<Boolean>?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (ignore) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support IGNORE in DELETE from join relation")
        }
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in DELETE from join relation")
        }
        val tableToDelete = targetTables.singleOrNull()
            ?: transaction.throwUnsupportedException(
                "PostgreSQL doesn't support DELETE from join relation with multiple tables to delete from"
            )
        targets.checkJoinTypes(StatementType.DELETE)

        return with(QueryBuilder(true)) {
            +"DELETE FROM "
            tableToDelete.describe(transaction, this)
            +" USING "
            appendJoinPart(tableToDelete, targets, transaction)
            where?.let {
                +" AND "
                +it
            }
            toString()
        }
    }

    override fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        return if (analyze && options != null) {
            super.explain(false, "ANALYZE TRUE, $options", internalStatement, transaction)
        } else {
            super.explain(analyze, options, internalStatement, transaction)
        }
    }

    override fun StringBuilder.appendOptionsToExplain(options: String) {
        append("($options) ")
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
 * PostgreSQL dialect implementation.
 */
open class PostgreSQLDialect(override val name: String = dialectName) : VendorDialect(dialectName, PostgreSQLDataTypeProvider, PostgreSQLFunctionProvider) {
    override val supportsSubqueryUnions: Boolean = true

    override val supportsOrderByNullsFirstLast: Boolean = true

    override val requiresAutoCommitOnCreateDrop: Boolean = true

    override val supportsWindowFrameGroupsMode: Boolean = true

    override val supportsColumnTypeChange: Boolean = true

    @Deprecated(
        "This method was moved to ExposedDatabaseMetadata and should not be used anymore from here.",
        ReplaceWith("currentDialectMetadata.areEquivalentColumnTypes(columnMetadataSqlType, columnMetadataJdbcType, columnType)")
    )
    @OptIn(InternalApi::class)
    override fun areEquivalentColumnTypes(columnMetadataSqlType: String, columnMetadataJdbcType: Int, columnType: String): Boolean =
        PostgreSQLColumnTypes.areEquivalent(columnMetadataSqlType, columnType)

    @Deprecated(
        "The parameter was moved to JdbcExposedDatabaseMetadata/R2dbcExposedDatabaseMetadata classes",
        ReplaceWith("TransactionManager.current().db.supportsSelectForUpdate")
    )
    override val supportsSelectForUpdate: Boolean = true

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    @OptIn(InternalApi::class)
    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        val transaction = currentTransaction()
        val colName = transaction.identity(column)
        var isDefaultReplaced = false
        val alterColumnParts = buildList {
            if (columnDiff.autoInc && column.autoIncColumnType != null) {
                val sequence = column.autoIncColumnType?.sequence
                if (sequence != null) {
                    add("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()}")
                    add("ALTER COLUMN $colName DROP DEFAULT")
                } else {
                    val fallbackSequenceName = fallbackSequenceName(tableName = column.table.tableName, columnName = column.name)
                    add("ALTER COLUMN $colName SET DEFAULT nextval('$fallbackSequenceName')")
                }
            } else if (columnDiff.autoInc && column.autoIncColumnType == null) {
                // based on logic in SchemaUtils.isIncorrectAutoInc this should only be possible if the existing
                // column in database is auto-incrementing while defined table is not
                add("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()}")
                add("ALTER COLUMN $colName DROP DEFAULT")
            } else if (columnDiff.type || columnDiff.sizeAndScale) {
                isDefaultReplaced = addColumnTypeChange(column, columnDiff, colName)
            }

            if (columnDiff.nullability) {
                val nullability = if (column.columnType.nullable) "DROP" else "SET"
                add("ALTER COLUMN $colName $nullability NOT NULL")
            }
            if (columnDiff.defaults && !isDefaultReplaced) {
                column.dbDefaultValue?.let {
                    add("ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
                } ?: run {
                    add("ALTER COLUMN $colName DROP DEFAULT")
                }
            }
        }

        val list = mutableListOf<String>()
        if (alterColumnParts.isNotEmpty()) {
            list += "ALTER TABLE ${transaction.identity(column.table)} ${alterColumnParts.joinToString()}"
        }
        if (columnDiff.autoInc && column.autoIncColumnType != null && column.autoIncColumnType?.sequence == null) {
            list.add(
                buildString {
                    val fallbackSequenceName = fallbackSequenceName(tableName = column.table.tableName, columnName = column.name)
                    val q = if (column.table.tableName.contains('.')) "\"" else ""
                    append("ALTER SEQUENCE $fallbackSequenceName OWNED BY $q${column.table.tableName.replace("\"", "")}.${column.name}$q")
                }
            )
        }
        if (columnDiff.comment) {
            list.addAll(setColumnComment(column, column.columnComment))
        }
        return list
    }

    /**
     * Adds the parts of an `ALTER TABLE` statement that change the type of [column] to this list,
     * and returns whether the default value of the column was also replaced by these parts.
     */
    @OptIn(InternalApi::class)
    private fun MutableList<String>.addColumnTypeChange(column: Column<*>, columnDiff: ColumnDiff, colName: String): Boolean {
        // SERIAL and BIGSERIAL are not real types, so the underlying integer type has to be used instead
        val sqlType = (column.autoIncColumnType?.delegate ?: column.columnType).sqlType()

        // Character types are excluded from an explicit cast, because it silently truncates values that are too long
        if (!columnDiff.type || column.autoIncColumnType != null || PostgreSQLColumnTypes.isCharacterType(sqlType)) {
            add("ALTER COLUMN $colName TYPE $sqlType")
            return false
        }

        // Types without an implicit conversion need an explicit cast, and an existing default would block the cast.
        // The column can only have a default in the database if one is defined or if the defaults differ.
        val hasDefault = column.dbDefaultValue != null || columnDiff.defaults
        if (hasDefault) add("ALTER COLUMN $colName DROP DEFAULT")
        add("ALTER COLUMN $colName TYPE $sqlType USING $colName::$sqlType")
        column.dbDefaultValue?.let {
            add("ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
        }
        return hasDefault
    }

    override fun createDatabase(name: String): String {
        @OptIn(InternalApi::class)
        return "CREATE DATABASE ${name.inProperCase()}"
    }

    override fun listDatabases(): String = "SELECT datname FROM pg_database"

    override fun dropDatabase(name: String): String {
        @OptIn(InternalApi::class)
        return "DROP DATABASE ${name.inProperCase()}"
    }

    override fun setSchema(schema: Schema): String = "SET search_path TO ${schema.identifier}"

    override fun createIndexWithType(name: String, table: String, columns: String, type: String, filterCondition: String): String {
        return "CREATE INDEX $name ON $table USING $type $columns$filterCondition"
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return if (isUnique && !isPartialOrFunctional) {
            "ALTER TABLE IF EXISTS ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT IF EXISTS ${identifierManager.quoteIfNecessary(indexName)}"
        } else {
            "DROP INDEX IF EXISTS ${identifierManager.cutIfNecessaryAndQuote(indexName)}"
        }
    }

    override fun setColumnComment(column: Column<*>, comment: String?): List<String> {
        @OptIn(InternalApi::class)
        val tr = currentTransaction()
        val fullColumnIdentity = tr.fullIdentity(column)

        return if (comment != null) {
            listOf("COMMENT ON COLUMN $fullColumnIdentity IS '${comment.escapeSingleQuotes()}'")
        } else {
            listOf("COMMENT ON COLUMN $fullColumnIdentity IS NULL")
        }
    }

    companion object : DialectNameProvider("PostgreSQL")
}

/**
 * PostgreSQL dialect implementation using the pgjdbc-ng jdbc driver.
 *
 * The driver accepts basic URLs in the following format : jdbc:pgsql://localhost:5432/db
 */
open class PostgreSQLNGDialect : PostgreSQLDialect(dialectName) {
    override val requiresAutoCommitOnCreateDrop: Boolean = true

    companion object : DialectNameProvider("PostgreSQLNG")
}

/**
 * Utility for comparing column types defined in Exposed with column types read from PostgreSQL database metadata.
 *
 * PostgreSQL reports column types using internal type names (for example, `int4`, `bpchar`, or `_varchar` for arrays),
 * so these need to be converted to the same form that [PostgreSQLDataTypeProvider] uses before they can be compared.
 */
/** @suppress */
@InternalApi
object PostgreSQLColumnTypes {
    private const val MAX_NUMERIC_PRECISION = 1000
    private const val MAX_VARCHAR_LENGTH = 10_485_760

    private val arrayDimensionsRegex = Regex("\\[\\d*]")
    private val commaRegex = Regex("\\s*,\\s*")

    /**
     * Builds a column type string from the [typeName] (the `pg_type.typname` value) and the [size] and [scale]
     * of a column, as reported by the database metadata.
     */
    fun fromMetadata(typeName: String, size: Int?, scale: Int?): String {
        val isArray = typeName.startsWith("_")
        val baseTypeName = typeName.removePrefix("_").lowercase()
        val baseType = when (baseTypeName) {
            "numeric" -> if (size != null && size in 1..MAX_NUMERIC_PRECISION) "DECIMAL($size, ${scale ?: 0})" else "DECIMAL"
            "bpchar" -> if (size != null && size > 0) "CHAR($size)" else "CHAR"
            "varchar" -> if (size != null && size in 1..MAX_VARCHAR_LENGTH) "VARCHAR($size)" else "VARCHAR"
            else -> typeNames[baseTypeName] ?: baseTypeName
        }
        return if (isArray) "$baseType[]" else baseType
    }

    private val typeNames = mapOf(
        "int2" to "SMALLINT",
        "int4" to "INT",
        "int8" to "BIGINT",
        "float4" to "REAL",
        "float8" to "DOUBLE PRECISION",
        "text" to "TEXT",
        "bool" to "BOOLEAN",
        "date" to "DATE",
        "time" to "TIME",
        "timetz" to "TIME WITH TIME ZONE",
        "timestamp" to "TIMESTAMP",
        "timestamptz" to "TIMESTAMP WITH TIME ZONE",
        "json" to "JSON",
        "jsonb" to "JSONB",
        "smallserial" to "SMALLSERIAL",
        "serial" to "SERIAL",
        "bigserial" to "BIGSERIAL"
    )

    /** Returns whether the [columnMetadataSqlType] built by [fromMetadata] is equivalent to the Exposed [columnType]. */
    fun areEquivalent(columnMetadataSqlType: String, columnType: String): Boolean {
        // Type information could not be retrieved, so there is nothing that can be reliably compared
        if (columnMetadataSqlType.isBlank()) return true

        val metadataType = normalize(columnMetadataSqlType)
        val definedType = normalize(columnType)
        if (metadataType == definedType) return true

        val isArray = metadataType.endsWith("[]")
        if (isArray != definedType.endsWith("[]")) return false

        val hasMetadataTypeArguments = columnMetadataSqlType.contains("(")
        val metadataBaseType = metadataType.substringBefore("(").removeSuffix("[]")
        val definedBaseType = definedType.substringBefore("(").removeSuffix("[]")
        return when {
            // Metadata does not always report the size of array elements
            isArray && !hasMetadataTypeArguments -> metadataBaseType == definedBaseType
            // Types that are not known by this utility are reported only by name, so the type arguments cannot be compared
            !hasMetadataTypeArguments && metadataBaseType !in knownTypes -> metadataBaseType == definedBaseType
            else -> false
        }
    }

    private val knownTypes = setOf(
        "SMALLINT", "INT", "BIGINT", "REAL", "DOUBLE PRECISION", "DECIMAL", "CHAR", "VARCHAR", "TEXT", "BOOLEAN", "DATE", "TIME",
        "TIME WITH TIME ZONE", "TIMESTAMP", "TIMESTAMP WITH TIME ZONE", "JSON", "JSONB", "BYTEA", "UUID"
    )

    /**
     * Returns a query for the full types of the columns of [tableName] whose size is not available in the standard metadata,
     * like the dimensions of pgvector types, as rows of the column name and its type.
     */
    fun extendedColumnTypesQuery(tableName: String): String =
        "SELECT a.attname, format_type(a.atttypid, a.atttypmod) FROM pg_catalog.pg_attribute a " +
            "JOIN pg_catalog.pg_type t ON t.oid = a.atttypid " +
            "WHERE a.attrelid = to_regclass('${tableName.replace("'", "''")}') AND a.attnum > 0 AND NOT a.attisdropped " +
            "AND t.typname IN ('vector', 'halfvec', 'sparsevec')"

    private val characterTypeRegex = Regex("^(CHAR|CHARACTER|VARCHAR|CHARACTER VARYING|TEXT)\\b.*")

    /** Returns whether [columnType] is a character type (or an array of one). */
    fun isCharacterType(columnType: String): Boolean = characterTypeRegex.matches(columnType.trim().uppercase())

    private fun normalize(type: String): String {
        val upperCaseType = type.trim().uppercase()
            .replace(arrayDimensionsRegex, "[]")
            .replace(commaRegex, ", ")
            .replace("NUMERIC", "DECIMAL")
        val isArray = upperCaseType.endsWith("[]")
        val baseType = when (val base = upperCaseType.removeSuffix("[]")) {
            // Auto-increment difference is dealt with elsewhere
            "SMALLSERIAL" -> "SMALLINT"
            "SERIAL" -> "INT"
            "BIGSERIAL" -> "BIGINT"
            "CHAR" -> "CHAR(1)"
            else -> base
        }
        return if (isArray) "$baseType[]" else baseType
    }
}
