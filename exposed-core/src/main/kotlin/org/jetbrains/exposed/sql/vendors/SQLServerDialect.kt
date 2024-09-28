package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.MergeStatement
import org.jetbrains.exposed.sql.statements.MergeStatement.ClauseAction.DELETE
import org.jetbrains.exposed.sql.statements.MergeStatement.ClauseAction.INSERT
import org.jetbrains.exposed.sql.statements.MergeStatement.ClauseAction.UPDATE
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.*

internal object SQLServerDataTypeProvider : DataTypeProvider() {
    override fun byteType(): String = if (currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.SQLServer) {
        "TINYINT"
    } else {
        "SMALLINT"
    }

    override fun ubyteType(): String {
        return if (currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.SQLServer) {
            "SMALLINT"
        } else {
            "TINYINT"
        }
    }

    override fun integerAutoincType(): String = "INT IDENTITY(1,1)"
    override fun longAutoincType(): String = "BIGINT IDENTITY(1,1)"
    override fun ulongAutoincType(): String = "NUMERIC(20) IDENTITY(1,1)"
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun blobType(): String = "VARBINARY(MAX)"
    override fun uuidType(): String = "uniqueidentifier"
    override fun uuidToDB(value: UUID): Any = value.toString()
    override fun dateTimeType(): String = "DATETIME2"
    override fun timestampWithTimeZoneType(): String =
        if (currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.SQLServer) {
            "TIMESTAMP(9) WITH TIME ZONE"
        } else {
            "DATETIMEOFFSET"
        }

    override fun booleanType(): String = "BIT"
    override fun booleanToStatementString(bool: Boolean): String = if (bool) "1" else "0"

    /**
     * varchar is used instead of "text" because it will be removed in future
     * https://docs.microsoft.com/en-us/sql/t-sql/data-types/ntext-text-and-image-transact-sql?view=sql-server-ver15
     */
    override fun textType(): String = "VARCHAR(MAX)"
    override fun mediumTextType(): String = textType()
    override fun largeTextType(): String = textType()
    override fun jsonType(): String = "NVARCHAR(MAX)"

    override fun precessOrderByClause(queryBuilder: QueryBuilder, expression: Expression<*>, sortOrder: SortOrder) {
        when (sortOrder) {
            SortOrder.ASC, SortOrder.DESC -> super.precessOrderByClause(queryBuilder, expression, sortOrder)
            SortOrder.ASC_NULLS_FIRST -> super.precessOrderByClause(queryBuilder, expression, SortOrder.ASC)
            SortOrder.DESC_NULLS_LAST -> super.precessOrderByClause(queryBuilder, expression, SortOrder.DESC)
            else -> {
                val sortOrderClause = if (sortOrder == SortOrder.ASC_NULLS_LAST) {
                    Expression.build {
                        Case().When(expression.isNull(), intLiteral(1)).Else(intLiteral(0))
                    } to SortOrder.ASC
                } else {
                    Expression.build {
                        Case().When(expression.isNull(), intLiteral(0)).Else(intLiteral(1))
                    } to SortOrder.DESC
                }
                queryBuilder.append(sortOrderClause.first, ", ")
                super.precessOrderByClause(queryBuilder, expression, sortOrderClause.second)
            }
        }
    }

    override fun hexToDb(hexString: String): String = "0x$hexString"
}

@Suppress("TooManyFunctions")
internal object SQLServerFunctionProvider : FunctionProvider() {
    override fun nextVal(seq: Sequence, builder: QueryBuilder) = builder {
        append("NEXT VALUE FOR ", seq.identifier)
    }

    override fun random(seed: Int?): String = if (seed != null) "RAND($seed)" else "RAND(CHECKSUM(NEWID()))"

    override fun <T : String?> charLength(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("LEN(", expr, ")")
    }

    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        val tr = TransactionManager.current()
        return when {
            expr.separator == null -> tr.throwUnsupportedException("SQL Server requires explicit separator in STRING_AGG")
            expr.distinct -> tr.throwUnsupportedException("SQL Server doesn't support DISTINCT in STRING_AGG")
            expr.orderBy.size > 1 -> tr.throwUnsupportedException("SQL Server supports only single column in ORDER BY clause in STRING_AGG")
            else -> queryBuilder {
                append("STRING_AGG(")
                append(expr.expr)
                append(", '${expr.separator}')")
                expr.orderBy.singleOrNull()?.let { (col, order) ->
                    append(" WITHIN GROUP (ORDER BY ", col, " ", order.name, ")")
                }
            }
        }
    }

    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("CHARINDEX(\'", substring, "\',", expr, ")")
    }

    override fun <T : String?> regexp(
        expr1: Expression<T>,
        pattern: Expression<String>,
        caseSensitive: Boolean,
        queryBuilder: QueryBuilder
    ): Unit = TransactionManager.current().throwUnsupportedException("SQLServer doesn't provide built in REGEXP expression, use LIKE instead.")

    override fun <T> date(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("CAST(", expr, " AS DATE)")
    }

    override fun <T> time(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("SUBSTRING(CONVERT(NVARCHAR, ", expr, ", 121), 12, 15)")
    }

    override fun <T> year(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(YEAR, ", expr, ")")
    }

    override fun <T> month(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(MONTH, ", expr, ")")
    }

    override fun <T> day(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(DAY, ", expr, ")")
    }

    override fun <T> hour(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(HOUR, ", expr, ")")
    }

    override fun <T> second(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(SECOND, ", expr, ")")
    }

    override fun <T> minute(expr: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("DATEPART(MINUTE, ", expr, ")")
    }

    override fun <T> stdDevPop(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STDEVP(", expression, ")")
    }

    override fun <T> stdDevSamp(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("STDEV(", expression, ")")
    }

    override fun <T> varPop(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("VARP(", expression, ")")
    }

    override fun <T> varSamp(expression: Expression<T>, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("VAR(", expression, ")")
    }

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType<*>,
        queryBuilder: QueryBuilder
    ) {
        if (path.size > 1) {
            TransactionManager.current().throwUnsupportedException("SQLServer does not support multiple JSON path arguments")
        }
        queryBuilder {
            append(if (toScalar) "JSON_VALUE" else "JSON_QUERY")
            append("(", expression, ", ")
            path.ifEmpty { arrayOf("") }.appendTo { +"'$$it'" }
            append(")")
        }
    }

    override fun update(
        target: Table,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        val def = super.update(target, columnsAndValues, null, where, transaction)
        return if (limit != null) def.replaceFirst("UPDATE", "UPDATE TOP($limit)") else def
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        val tableToUpdate = columnsAndValues.map { it.first.table }.distinct().singleOrNull()
            ?: transaction.throwUnsupportedException("SQLServer supports a join updates with a single table columns to update.")

        targets.checkJoinTypes(StatementType.UPDATE)
        if (limit != null) {
            +"UPDATE TOP($limit) "
        } else {
            +"UPDATE "
        }
        tableToUpdate.describe(transaction, this)
        +" SET "
        columnsAndValues.appendTo(this) { (col, value) ->
            append("${transaction.fullIdentity(col)}=")
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
        // SQLSERVER MERGE statement must be terminated by a semi-colon (;)
        return super.upsert(table, data, expression, onUpdate, keyColumns, where, transaction) + ";"
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        val def = super.delete(ignore, table, where, null, transaction)
        return if (limit != null) def.replaceFirst("DELETE", "DELETE TOP($limit)") else def
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
            transaction.throwUnsupportedException("SQL Server doesn't support IGNORE in DELETE from join relation")
        }
        val tableToDelete = targetTables.singleOrNull()
            ?: transaction.throwUnsupportedException(
                "SQL Server doesn't support DELETE from join relation with multiple tables to delete from"
            )
        targets.checkJoinTypes(StatementType.DELETE)

        return with(QueryBuilder(true)) {
            +"DELETE "
            limit?.let {
                +"TOP($it) "
            }
            +"FROM "
            if (tableToDelete is Alias<*>) {
                +tableToDelete.alias
            } else {
                tableToDelete.describe(transaction, this)
            }
            +" FROM "
            appendJoinPart(tableToDelete, targets, transaction, filterTargetTable = false)
            where?.let {
                +" AND "
                +it
            }
            toString()
        }
    }

    override fun queryLimitAndOffset(size: Int?, offset: Long, alreadyOrdered: Boolean): String = buildString {
        if (!alreadyOrdered) {
            append("ORDER BY(SELECT NULL) ")
        }
        append("OFFSET $offset ROWS")
        size?.let {
            append(" FETCH NEXT $size ROWS ONLY")
        }
    }

    override fun explain(
        analyze: Boolean,
        options: String?,
        internalStatement: String,
        transaction: Transaction
    ): String {
        transaction.throwUnsupportedException(
            "EXPLAIN queries are not currently supported for SQL Server. Please log a YouTrack feature extension request."
        )
    }

    override fun merge(dest: Table, source: Table, transaction: Transaction, clauses: List<MergeStatement.Clause>, on: Op<Boolean>?): String {
        validateMergeCommandClauses(transaction, clauses)
        return super.merge(dest, source, transaction, clauses, on) + ";"
    }

    override fun mergeSelect(
        dest: Table,
        source: QueryAlias,
        transaction: Transaction,
        clauses: List<MergeStatement.Clause>,
        on: Op<Boolean>,
        prepared: Boolean
    ): String {
        validateMergeCommandClauses(transaction, clauses)
        return super.mergeSelect(dest, source, transaction, clauses, on, prepared) + ";"
    }
}

private fun validateMergeCommandClauses(transaction: Transaction, clauses: List<MergeStatement.Clause>) {
    when {
        clauses.count { it.action == INSERT } > 1 ->
            transaction.throwUnsupportedException("Multiple insert clauses are not supported by DB")
        clauses.count { it.action == UPDATE } > 1 ->
            transaction.throwUnsupportedException("Multiple update clauses are not supported by DB")
        clauses.count { it.action == DELETE } > 1 ->
            transaction.throwUnsupportedException("Multiple delete clauses are not supported by DB")
    }
}

/**
 * SQLServer dialect implementation.
 */
open class SQLServerDialect : VendorDialect(dialectName, SQLServerDataTypeProvider, SQLServerFunctionProvider) {
    override val supportsIfNotExists: Boolean = false
    override val defaultReferenceOption: ReferenceOption get() = ReferenceOption.NO_ACTION
    override val needsQuotesWhenSymbolsInNames: Boolean = false
    override val supportsSequenceAsGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true
    override val supportsRestrictReferenceOption: Boolean = false

    private val nonAcceptableDefaults = arrayOf("DEFAULT")

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean {
        val columnDefault = e.toString().uppercase().trim()
        return columnDefault !in nonAcceptableDefaults
    }

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        val transaction = TransactionManager.current()

        val alterTablePart = "ALTER TABLE ${transaction.identity(column.table)} "

        val statements = mutableListOf<String>()

        val autoIncColumnType = column.autoIncColumnType
        val replaceWithNewColumn = columnDiff.autoInc && autoIncColumnType != null && autoIncColumnType.sequence == null

        statements.add(
            buildString {
                if (replaceWithNewColumn) {
                    append(alterTablePart + "ADD NEW_${transaction.identity(column)} ${column.columnType.sqlType()}")
                } else {
                    append(alterTablePart + "ALTER COLUMN ${transaction.identity(column)} ${column.columnType.sqlType()}")
                }

                if (columnDiff.nullability) {
                    val defaultValue = column.dbDefaultValue
                    val isPKColumn = column.table.primaryKey?.columns?.contains(column) == true

                    if (column.columnType.nullable ||
                        (defaultValue != null && column.defaultValueFun == null && !currentDialect.isAllowedAsColumnDefault(defaultValue))
                    ) {
                        append(" NULL")
                    } else if (!isPKColumn) {
                        append(" NOT NULL")
                    }
                }
            }
        )

        if (columnDiff.defaults) {
            val tableName = column.table.tableName
            val columnName = column.name
            val constraintName = "DF_${tableName}_$columnName"

            val dropConstraint = "DROP CONSTRAINT IF EXISTS $constraintName"

            statements.add(
                buildString {
                    column.dbDefaultValue?.let {
                        append(alterTablePart + dropConstraint)
                        append("; ")
                        append(
                            alterTablePart +
                                "ADD CONSTRAINT $constraintName DEFAULT ${SQLServerDataTypeProvider.processForDefaultValue(it)} for ${transaction.identity(column)}"
                        )
                    } ?: append(alterTablePart + dropConstraint)
                }
            )
        }

        if (replaceWithNewColumn) {
            with(statements) {
                add(alterTablePart + "DROP COLUMN ${transaction.identity(column)}")
                add("EXEC sp_rename '${transaction.identity(column.table)}.NEW_${transaction.identity(column)}', '${transaction.identity(column)}', 'COLUMN'")
            }
        }

        return statements
    }

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun listDatabases(): String = "SELECT name FROM sys.databases"

    override fun dropDatabase(name: String) = "DROP DATABASE ${name.inProperCase()}"

    override fun setSchema(schema: Schema): String = "ALTER USER ${schema.authorization} WITH DEFAULT_SCHEMA = ${schema.identifier}"

    override fun createSchema(schema: Schema): String = buildString {
        append("CREATE SCHEMA ", schema.identifier)
        appendIfNotNull(" AUTHORIZATION ", schema.authorization)
    }

    override fun dropSchema(schema: Schema, cascade: Boolean): String = "DROP SCHEMA ${schema.identifier}"

    override fun createIndex(index: Index): String {
        if (index.functions != null) {
            exposedLogger.warn(
                "Functional index on ${index.table.tableName} using ${index.functions.joinToString { it.toString() }} can't be created in SQLServer"
            )
            return ""
        }
        return super.createIndex(index)
    }

    override fun createIndexWithType(
        name: String,
        table: String,
        columns: String,
        type: String,
        filterCondition: String
    ): String {
        return "CREATE $type INDEX $name ON $table $columns$filterCondition"
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return if (isUnique && !isPartialOrFunctional) {
            "ALTER TABLE ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT IF EXISTS ${identifierManager.quoteIfNecessary(indexName)}"
        } else {
            "DROP INDEX IF EXISTS ${identifierManager.quoteIfNecessary(indexName)} ON ${identifierManager.quoteIfNecessary(tableName)}"
        }
    }

    // https://docs.microsoft.com/en-us/sql/t-sql/language-elements/like-transact-sql?redirectedfrom=MSDN&view=sql-server-ver15#arguments
    override val likePatternSpecialChars = sqlServerLikePatternSpecialChars

    override fun sequences(): List<String> {
        val sequences = mutableListOf<String>()
        TransactionManager.current().exec("SELECT name FROM sys.sequences") { rs ->
            while (rs.next()) {
                sequences.add(rs.getString("name"))
            }
        }
        return sequences
    }

    companion object : DialectNameProvider("SQLServer") {
        private val sqlServerLikePatternSpecialChars = mapOf('%' to null, '_' to null, '[' to ']')
    }
}
