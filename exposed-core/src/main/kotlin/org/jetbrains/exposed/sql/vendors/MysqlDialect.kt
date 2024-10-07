package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal

internal object MysqlDataTypeProvider : DataTypeProvider() {

    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun dateTimeType(): String = if ((currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true) "DATETIME(6)" else "DATETIME"

    override fun timestampWithTimeZoneType(): String =
        if ((currentDialect as? MysqlDialect)?.isTimeZoneOffsetSupported() == true) {
            "TIMESTAMP(6)"
        } else {
            throw UnsupportedByDialectException(
                "This vendor does not support timestamp with time zone data type" +
                    ((currentDialect as? MariaDBDialect)?.let { "" } ?: " for this version"),
                currentDialect
            )
        }

    override fun ubyteType(): String = "TINYINT UNSIGNED"

    override fun ushortType(): String = "SMALLINT UNSIGNED"

    override fun uintegerType(): String = "INT UNSIGNED"

    override fun uintegerAutoincType(): String = "INT UNSIGNED AUTO_INCREMENT"

    override fun ulongType(): String = "BIGINT UNSIGNED"

    override fun ulongAutoincType(): String = "BIGINT UNSIGNED AUTO_INCREMENT"

    override fun textType(): String = "text"

    /** Character type for storing strings of variable and _unlimited_ length. */
    override fun mediumTextType(): String = "MEDIUMTEXT"

    /** Character type for storing strings of variable and _unlimited_ length. */
    override fun largeTextType(): String = "LONGTEXT"

    override fun booleanFromStringToBoolean(value: String): Boolean = when (value) {
        "0" -> false
        "1" -> true
        else -> value.toBoolean()
    }

    override fun jsonBType(): String = "JSON"

    override fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> && e.columnType is JsonColumnMarker -> when {
            currentDialect is MariaDBDialect -> super.processForDefaultValue(e)
            ((currentDialect as? MysqlDialect)?.fullVersion ?: "0") >= "8.0.13" -> "(${super.processForDefaultValue(e)})"
            else -> throw UnsupportedByDialectException(
                "MySQL versions prior to 8.0.13 do not accept default values on JSON columns",
                currentDialect
            )
        }
        currentDialect is MariaDBDialect -> super.processForDefaultValue(e)
        // The default value specified in a DEFAULT clause can be a literal constant or an expression. With one
        // exception, enclose expression default values within parentheses to distinguish them from literal constant
        // default values. The exception is that, for TIMESTAMP and DATETIME columns, you can specify the
        // CURRENT_TIMESTAMP function as the default, without enclosing parentheses.
        // https://dev.mysql.com/doc/refman/8.0/en/data-type-defaults.html#data-type-defaults-explicit
        e is ExpressionWithColumnType<*> && e.columnType is IDateColumnType && e.toString().startsWith("CURRENT_TIMESTAMP") ->
            super.processForDefaultValue(e)
        e !is LiteralOp<*> && ((currentDialect as? MysqlDialect)?.fullVersion ?: "0") >= "8.0.13" ->
            "(${super.processForDefaultValue(e)})"
        else -> super.processForDefaultValue(e)
    }

    override fun precessOrderByClause(queryBuilder: QueryBuilder, expression: Expression<*>, sortOrder: SortOrder) {
        when (sortOrder) {
            SortOrder.ASC, SortOrder.DESC -> super.precessOrderByClause(queryBuilder, expression, sortOrder)
            SortOrder.ASC_NULLS_FIRST -> super.precessOrderByClause(queryBuilder, expression, SortOrder.ASC)
            SortOrder.DESC_NULLS_LAST -> super.precessOrderByClause(queryBuilder, expression, SortOrder.DESC)
            else -> {
                val exp = (expression as? ExpressionAlias<*>)?.alias ?: expression
                val nullExp = if (sortOrder == SortOrder.ASC_NULLS_LAST) " IS NULL" else " IS NOT NULL"
                val order = if (sortOrder == SortOrder.ASC_NULLS_LAST) SortOrder.ASC else SortOrder.DESC
                queryBuilder.append(exp, nullExp, ", ", exp, " ", order.code)
            }
        }
    }

    override fun hexToDb(hexString: String): String = "0x$hexString"
}

internal open class MysqlFunctionProvider : FunctionProvider() {
    internal object INSTANCE : MysqlFunctionProvider()

    override fun random(seed: Int?): String = "RAND(${seed?.toString().orEmpty()})"

    private class MATCH(val expr: Expression<*>, val pattern: String, val mode: MatchMode) : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("MATCH(", expr, ") AGAINST ('", pattern, "' ", mode.mode(), ")")
        }
    }

    private enum class MysqlMatchMode(val operator: String) : MatchMode {
        STRICT("IN BOOLEAN MODE"),
        NATURAL_LANGUAGE("IN NATURAL LANGUAGE MODE");

        override fun mode() = operator
    }

    override fun <T : String?> Expression<T>.match(pattern: String, mode: MatchMode?): Op<Boolean> =
        MATCH(this, pattern, mode ?: MysqlMatchMode.STRICT)

    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("LOCATE(\'", substring, "\',", expr, ")")
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ) {
        return if ((currentDialect as MysqlDialect).isMysql8) {
            super.regexp(expr1, pattern, caseSensitive, queryBuilder)
        } else {
            queryBuilder { append(expr1, " REGEXP ", pattern) }
        }
    }

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) = queryBuilder {
        if (toScalar) append("JSON_UNQUOTE(")
        append("JSON_EXTRACT(", expression, ", ")
        path.ifEmpty { arrayOf("") }.appendTo { +"\"$$it\"" }
        append(")${if (toScalar) ")" else ""}")
    }

    override fun jsonContains(
        target: Expression<*>,
        candidate: Expression<*>,
        path: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) = queryBuilder {
        append("JSON_CONTAINS(", target, ", ", candidate)
        path?.let {
            append(", '$$it'")
        }
        append(")")
    }

    override fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        val oneOrAll = optional?.lowercase()
        if (oneOrAll != "one" && oneOrAll != "all") {
            TransactionManager.current().throwUnsupportedException("MySQL requires a single optional argument: 'one' or 'all'")
        }
        queryBuilder {
            append("JSON_CONTAINS_PATH(", expression, ", ")
            append("'$oneOrAll', ")
            path.ifEmpty { arrayOf("") }.appendTo { +"'$$it'" }
            append(")")
        }
    }

    override fun replace(
        table: Table,
        columns: List<Column<*>>,
        expression: String,
        transaction: Transaction,
        prepared: Boolean
    ): String {
        val insertStatement = super.insert(false, table, columns, expression, transaction)
        return insertStatement.replace("INSERT", "REPLACE")
    }

    private object CharColumnType : StringColumnType() {
        override fun sqlType(): String = "CHAR"
    }

    override fun cast(expr: Expression<*>, type: IColumnType<*>, builder: QueryBuilder) = when (type) {
        is StringColumnType -> super.cast(expr, CharColumnType, builder)
        else -> super.cast(expr, type, builder)
    }

    override val DEFAULT_VALUE_EXPRESSION: String = "() VALUES ()"

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) def.replaceFirst("INSERT", "INSERT IGNORE") else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        val def = super.delete(false, table, where, limit, transaction)
        return if (ignore) def.replaceFirst("DELETE", "DELETE IGNORE") else def
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("MySQL doesn't support LIMIT in UPDATE with join clause.")
        }

        return with(QueryBuilder(true)) {
            +"UPDATE "
            targets.describe(transaction, this)
            +" SET "
            columnsAndValues.appendTo(this) { (col, value) ->
                append("${transaction.fullIdentity(col)}=")
                registerArgument(col, value)
            }

            where?.let {
                +" WHERE "
                +it
            }
            toString()
        }
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
        if (keyColumns.isNotEmpty()) {
            transaction.throwUnsupportedException("MySQL doesn't support specifying conflict keys in UPSERT clause")
        }
        if (where != null) {
            transaction.throwUnsupportedException("MySQL doesn't support WHERE in UPSERT clause")
        }

        return with(QueryBuilder(true)) {
            +insert(false, table, data.unzip().first, expression, transaction)
            if (isUpsertAliasSupported(transaction.db.dialect)) {
                +" AS NEW"
            }

            +" ON DUPLICATE KEY UPDATE "
            onUpdate.appendTo { (columnToUpdate, updateExpression) ->
                append("${transaction.identity(columnToUpdate)}=")
                registerArgument(columnToUpdate, updateExpression)
            }
            toString()
        }
    }

    override fun delete(
        ignore: Boolean,
        targets: Join,
        targetTables: List<Table>,
        where: Op<Boolean>?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("${currentDialect.name} doesn't support LIMIT in DELETE from join relation")
        }
        return with(QueryBuilder(true)) {
            +"DELETE "
            if (ignore) {
                +"IGNORE "
            }
            targetTables.appendTo { target ->
                when (target) {
                    is Alias<*> -> +target.alias
                    else -> target.describe(transaction, this)
                }
            }
            +" FROM "
            targets.describe(transaction, this)
            where?.let {
                +" WHERE "
                +it
            }
            toString()
        }
    }

    override fun insertValue(columnName: String, queryBuilder: QueryBuilder) {
        queryBuilder {
            if (isUpsertAliasSupported(currentDialect)) {
                +"NEW.$columnName"
            } else {
                +"VALUES($columnName)"
            }
        }
    }

    private fun isUpsertAliasSupported(dialect: DatabaseDialect): Boolean = when (dialect) {
        is MysqlDialect -> dialect !is MariaDBDialect && dialect.fullVersion >= "8.0.19"
        else -> false // H2_MySQL mode also uses this function provider & requires older unsupported version
    }

    override fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("SUBSTRING_INDEX(", expr, ", ' ', -1)")
    }

    override fun queryLimitAndOffset(size: Int?, offset: Long, alreadyOrdered: Boolean): String {
        if (size == null && offset > 0) {
            TransactionManager.current().throwUnsupportedException(
                "${currentDialect.name} doesn't support OFFSET clause without LIMIT"
            )
        }
        return super.queryLimitAndOffset(size, offset, alreadyOrdered)
    }
}

/**
 * MySQL dialect implementation.
 */
open class MysqlDialect : VendorDialect(dialectName, MysqlDataTypeProvider, MysqlFunctionProvider.INSTANCE) {

    internal val isMysql8: Boolean by lazy {
        TransactionManager.current().db.isVersionCovers(BigDecimal("8.0"))
    }

    internal val fullVersion: String by lazy {
        TransactionManager.current().db.metadata { databaseProductVersion }
    }

    override val supportsCreateSequence: Boolean = false

    override val supportsTernaryAffectedRowValues: Boolean = true

    override val supportsSubqueryUnions: Boolean = true

    override val supportsOrderByNullsFirstLast: Boolean = false

    override val supportsSetDefaultReferenceOption: Boolean = false

    /** Returns `true` if the MySQL JDBC connector version is greater than or equal to 5.6. */
    fun isFractionDateTimeSupported(): Boolean = TransactionManager.current().db.isVersionCovers(BigDecimal("5.6"))

    /** Returns `true` if a MySQL JDBC connector is being used and its version is greater than or equal to 8.0. */
    fun isTimeZoneOffsetSupported(): Boolean = (currentDialect !is MariaDBDialect) && isMysql8

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean {
        if (super.isAllowedAsColumnDefault(e)) return true
        if ((currentDialect is MariaDBDialect && fullVersion >= "10.2.1") || (currentDialect !is MariaDBDialect && fullVersion >= "8.0.13")) {
            return true
        }
        val acceptableDefaults = mutableListOf("CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP()", "NOW()", "CURRENT_TIMESTAMP(6)", "NOW(6)")
        return e.toString().trim() in acceptableDefaults && isFractionDateTimeSupported()
    }

    override fun createIndex(index: Index): String {
        if (index.functions != null && !isMysql8) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in MySQL prior to 8.0"
            )
            return ""
        }
        return super.createIndex(index)
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String =
        "ALTER TABLE ${identifierManager.quoteIfNecessary(tableName)} DROP INDEX ${identifierManager.quoteIfNecessary(indexName)}"

    override fun setSchema(schema: Schema): String = "USE ${schema.identifier}"

    override fun createSchema(schema: Schema): String = buildString {
        append("CREATE SCHEMA IF NOT EXISTS ", schema.identifier)

        if (schema.authorization != null) {
            throw UnsupportedByDialectException(
                "${currentDialect.name} do not have database owners. " +
                    "You can use GRANT to allow or deny rights on database.",
                currentDialect
            )
        }
    }

    override fun dropSchema(schema: Schema, cascade: Boolean): String = "DROP SCHEMA IF EXISTS ${schema.identifier}"

    override fun String.metadataMatchesTable(schema: String, table: Table): Boolean {
        return when {
            schema.isEmpty() -> this == table.nameInDatabaseCaseUnquoted()
            else -> {
                val sanitizedTableName = table.tableNameWithoutScheme.replace("`", "")
                val nameInDb = "$schema.$sanitizedTableName".inProperCase()
                this == nameInDb
            }
        }
    }

    companion object : DialectNameProvider("MySQL")
}
