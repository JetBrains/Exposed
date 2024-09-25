package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

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
    override fun uuidToDB(value: UUID): Any = value
    override fun dateTimeType(): String = "TIMESTAMP"
    override fun jsonBType(): String = "JSONB"

    override fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> && e.columnType is JsonColumnMarker && (currentDialect as? H2Dialect) == null -> {
            val cast = if (e.columnType.usesBinaryFormat) "::jsonb" else "::json"
            "${super.processForDefaultValue(e)}$cast"
        }
        e is LiteralOp<*> && e.columnType is BlobColumnType && e.columnType.useObjectIdentifier && (currentDialect as? H2Dialect) == null -> {
            "lo_from_bytea(0, ${super.processForDefaultValue(e)} :: bytea)"
        }
        e is LiteralOp<*> && e.columnType is ArrayColumnType<*, *> -> {
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
        val tr = TransactionManager.current()
        return when (expr.separator) {
            null -> tr.throwUnsupportedException("PostgreSQL requires explicit separator in STRING_AGG function.")
            else -> queryBuilder {
                append("STRING_AGG(")
                if (expr.distinct) append(" DISTINCT ")
                append(expr.expr, ", '", expr.separator, "'")
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
        append("POSITION(\'", substring, "\' IN ", expr, ")")
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
        path.ifEmpty { arrayOf("$") }.appendTo { +"'$it'" }
        append(")")
    }

    override fun jsonContains(
        target: Expression<*>,
        candidate: Expression<*>,
        path: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        path?.let {
            TransactionManager.current().throwUnsupportedException("PostgreSQL does not support a JSON path argument")
        }
        val isNotJsonB = !(jsonType as JsonColumnMarker).usesBinaryFormat
        queryBuilder {
            append(target)
            if (isNotJsonB) append("::jsonb")
            append(" @> ", candidate)
            if (isNotJsonB) append("::jsonb")
        }
    }

    override fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        if (path.size > 1) {
            TransactionManager.current().throwUnsupportedException("PostgreSQL does not support multiple JSON path arguments")
        }
        val isNotJsonB = !(jsonType as JsonColumnMarker).usesBinaryFormat
        queryBuilder {
            append("JSONB_PATH_EXISTS(")
            if (isNotJsonB) {
                append("CAST(", expression, " as jsonb), ")
            } else {
                append(expression, ", ")
            }
            append("'$", path.firstOrNull() ?: "", "'")
            optional?.let {
                append(", '$it'")
            }
            append(")")
        }
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
        target: Table,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in UPDATE clause.")
        }
        return super.update(target, columnsAndValues, null, where, transaction)
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

    override fun insertValue(columnName: String, queryBuilder: QueryBuilder) { queryBuilder { +"EXCLUDED.$columnName" } }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("PostgreSQL doesn't support LIMIT in DELETE clause.")
        }
        return super.delete(ignore, table, where, null, transaction)
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

    override fun StringBuilder.appendOptionsToExplain(options: String) { append("($options) ") }

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

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        val list = mutableListOf(
            buildString {
                val tr = TransactionManager.current()
                append("ALTER TABLE ${tr.identity(column.table)} ")
                val colName = tr.identity(column)

                if (columnDiff.autoInc && column.autoIncColumnType != null) {
                    val sequence = column.autoIncColumnType?.sequence
                    if (sequence != null) {
                        append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()}")
                        append(", ALTER COLUMN $colName DROP DEFAULT")
                    } else {
                        val fallbackSequenceName = fallbackSequenceName(tableName = column.table.tableName, columnName = column.name)
                        append("ALTER COLUMN $colName SET DEFAULT nextval('$fallbackSequenceName')")
                    }
                } else {
                    append("ALTER COLUMN $colName TYPE ${column.columnType.sqlType()}")
                }

                if (columnDiff.nullability) {
                    append(", ALTER COLUMN $colName ")
                    if (column.columnType.nullable) {
                        append("DROP ")
                    } else {
                        append("SET ")
                    }
                    append("NOT NULL")
                }
                if (columnDiff.defaults) {
                    column.dbDefaultValue?.let {
                        append(", ALTER COLUMN $colName SET DEFAULT ${PostgreSQLDataTypeProvider.processForDefaultValue(it)}")
                    } ?: run {
                        append(", ALTER COLUMN $colName DROP DEFAULT")
                    }
                }
            }
        )
        if (columnDiff.autoInc && column.autoIncColumnType != null && column.autoIncColumnType?.sequence == null) {
            list.add(
                buildString {
                    val fallbackSequenceName = fallbackSequenceName(tableName = column.table.tableName, columnName = column.name)
                    val q = if (column.table.tableName.contains('.')) "\"" else ""
                    append("ALTER SEQUENCE $fallbackSequenceName OWNED BY $q${column.table.tableName.replace("\"", "")}.${column.name}$q")
                }
            )
        }
        return list
    }

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun listDatabases(): String = "SELECT datname FROM pg_database"

    override fun dropDatabase(name: String): String = "DROP DATABASE ${name.inProperCase()}"

    override fun setSchema(schema: Schema): String = "SET search_path TO ${schema.identifier}"

    override fun createIndexWithType(name: String, table: String, columns: String, type: String, filterCondition: String): String {
        return "CREATE INDEX $name ON $table USING $type $columns$filterCondition"
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return if (isUnique && !isPartialOrFunctional) {
            "ALTER TABLE IF EXISTS ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT IF EXISTS ${identifierManager.quoteIfNecessary(indexName)}"
        } else {
            "DROP INDEX IF EXISTS ${identifierManager.quoteIfNecessary(indexName)}"
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
