package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.DatabaseMetaData
import java.util.*

internal object OracleDataTypeProvider : DataTypeProvider() {
    override fun byteType(): String = "SMALLINT"
    override fun ubyteType(): String = "NUMBER(4)"
    override fun ushortType(): String = "NUMBER(6)"
    override fun integerType(): String = "NUMBER(12)"
    override fun integerAutoincType(): String = "NUMBER(12)"
    override fun uintegerType(): String = "NUMBER(13)"
    override fun longType(): String = "NUMBER(19)"
    override fun longAutoincType(): String = "NUMBER(19)"
    override fun ulongType(): String = "NUMBER(20)"
    override fun varcharType(colLength: Int): String = "VARCHAR2($colLength CHAR)"
    override fun textType(): String = "CLOB"
    override fun mediumTextType(): String = textType()
    override fun largeTextType(): String = textType()
    override fun timeType(): String = dateTimeType()
    override fun binaryType(): String {
        exposedLogger.error("Binary type is unsupported for Oracle. Please use blob column type instead.")
        error("Binary type is unsupported for Oracle. Please use blob column type instead.")
    }

    override fun binaryType(length: Int): String {
        @Suppress("MagicNumber")
        return if (length < 2000) "RAW ($length)" else binaryType()
    }

    override fun uuidType(): String {
        return if ((currentDialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            "UUID"
        } else {
            return "RAW(16)"
        }
    }

    override fun uuidToDB(value: UUID): Any {
        return if ((currentDialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            H2DataTypeProvider.uuidToDB(value)
        } else {
            super.uuidToDB(value)
        }
    }

    override fun dateTimeType(): String = "TIMESTAMP"
    override fun booleanType(): String = "CHAR(1)"
    override fun booleanToStatementString(bool: Boolean) = if (bool) "1" else "0"
    override fun booleanFromStringToBoolean(value: String): Boolean = try {
        value.toLong() != 0L
    } catch (ex: NumberFormatException) {
        @Suppress("SwallowedException")
        try {
            value.lowercase().toBooleanStrict()
        } catch (ex: IllegalArgumentException) {
            error("Unexpected value of type Boolean: $value")
        }
    }

    override fun jsonType(): String = "VARCHAR2(4000)"

    override fun processForDefaultValue(e: Expression<*>): String = when {
        e is LiteralOp<*> && (e.columnType as? IDateColumnType)?.hasTimePart == false -> "DATE ${super.processForDefaultValue(e)}"
        e is LiteralOp<*> && e.columnType is IDateColumnType -> "TIMESTAMP ${super.processForDefaultValue(e)}"
        else -> super.processForDefaultValue(e)
    }

    override fun hexToDb(hexString: String): String = "HEXTORAW('$hexString')"
}

internal object OracleFunctionProvider : FunctionProvider() {

    /**
     * SQL function that generates a random value uniformly distributed between 0 (inclusive) and 1 (exclusive).
     *
     * **Note:** Oracle ignores the [seed]. You have to use the `dbms_random.seed` function manually.
     */
    override fun random(seed: Int?): String = "dbms_random.value"

    override fun <T : String?> charLength(expr: Expression<T>, queryBuilder: QueryBuilder) = queryBuilder {
        append("LENGTH(", expr, ")")
    }

    override fun <T : String?> substring(
        expr: Expression<T>,
        start: Expression<Int>,
        length: Expression<Int>,
        builder: QueryBuilder,
        prefix: String
    ): Unit = super.substring(expr, start, length, builder, "SUBSTR")

    override fun concat(
        separator: String,
        queryBuilder: QueryBuilder,
        vararg expr: Expression<*>
    ): Unit = queryBuilder {
        if (separator == "") {
            expr.appendTo(separator = " || ") { +it }
        } else {
            expr.appendTo(separator = " || '$separator' || ") { +it }
        }
    }

    override fun <T : String?> groupConcat(
        expr: GroupConcat<T>,
        queryBuilder: QueryBuilder
    ): Unit = queryBuilder {
        if (expr.orderBy.size != 1) {
            TransactionManager.current().throwUnsupportedException("SQLServer supports only single column in ORDER BY clause in LISTAGG")
        }
        append("LISTAGG(")
        append(expr.expr)
        expr.separator?.let {
            append(", '$it'")
        }
        append(") WITHIN GROUP (ORDER BY ")
        val (col, order) = expr.orderBy.single()
        append(col, " ", order.name, ")")
    }

    override fun <T : String?> locate(
        queryBuilder: QueryBuilder,
        expr: Expression<T>,
        substring: String
    ) = queryBuilder {
        append("INSTR(", expr, ",\'", substring, "\')")
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

    override fun <T> jsonExtract(
        expression: Expression<T>,
        vararg path: String,
        toScalar: Boolean,
        jsonType: IColumnType,
        queryBuilder: QueryBuilder
    ) {
        if (path.size > 1) {
            TransactionManager.current().throwUnsupportedException("Oracle does not support multiple JSON path arguments")
        }
        queryBuilder {
            append(if (toScalar) "JSON_VALUE" else "JSON_QUERY")
            append("(", expression, ", ")
            append("'$", path.firstOrNull() ?: "", "'")
            append(")")
        }
    }

    override fun jsonExists(
        expression: Expression<*>,
        vararg path: String,
        optional: String?,
        jsonType: IColumnType,
        queryBuilder: QueryBuilder
    ) {
        if (path.size > 1) {
            TransactionManager.current().throwUnsupportedException("Oracle does not support multiple JSON path arguments")
        }
        queryBuilder {
            append("JSON_EXISTS(", expression, ", ")
            append("'$", path.firstOrNull() ?: "", "'")
            optional?.let {
                append(" $it")
            }
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
        return when {
            limit != null && where != null -> "$def AND ROWNUM <= $limit"
            limit != null -> "$def WHERE ROWNUM <= $limit"
            else -> def
        }
    }

    override fun update(
        targets: Join,
        columnsAndValues: List<Pair<Column<*>, Any?>>,
        limit: Int?,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String = with(QueryBuilder(true)) {
        columnsAndValues.map { it.first.table }.distinct().singleOrNull()
            ?: transaction.throwUnsupportedException("Oracle supports a join updates with a single table columns to update.")
        if (targets.joinParts.any { it.joinType != JoinType.INNER }) {
            exposedLogger.warn("All tables in UPDATE statement will be joined with inner join")
        }
        +"UPDATE ("
        val columnsToSelect = columnsAndValues.flatMap {
            listOfNotNull(it.first, it.second as? Expression<*>)
        }.mapIndexed { index, expression -> expression to expression.alias("c$index") }.toMap()

        val subQuery = targets.select(columnsToSelect.values.toList())
        where?.let {
            subQuery.adjustWhere { it }
        }
        subQuery.prepareSQL(this)
        +") x"

        columnsAndValues.appendTo(this, prefix = " SET ") { (col, value) ->
            val alias = columnsToSelect.getValue(col)
            +alias.alias
            +"="
            (value as? Expression<*>)?.let {
                +columnsToSelect.getValue(it).alias
            } ?: registerArgument(col, value)
        }

        limit?.let {
            +" WHERE ROWNUM <= $it"
        }

        toString()
    }

    override fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        onUpdate: List<Pair<Column<*>, Expression<*>>>?,
        where: Op<Boolean>?,
        transaction: Transaction,
        vararg keys: Column<*>
    ): String {
        val statement = super.upsert(table, data, onUpdate, where, transaction, *keys)

        val dualTable = data.appendTo(QueryBuilder(true), prefix = "(SELECT ", postfix = " FROM DUAL) S") { (column, value) ->
            registerArgument(column, value)
            +" AS "
            append(transaction.identity(column))
        }.toString()

        val (leftReserved, rightReserved) = " USING " to " ON "
        val leftBoundary = statement.indexOf(leftReserved) + leftReserved.length
        val rightBoundary = statement.indexOf(rightReserved)
        return statement.replaceRange(leftBoundary, rightBoundary, dualTable)
    }

    override fun delete(
        ignore: Boolean,
        table: Table,
        where: String?,
        limit: Int?,
        transaction: Transaction
    ): String {
        if (limit != null) {
            transaction.throwUnsupportedException("Oracle doesn't support LIMIT in DELETE clause.")
        }
        return super.delete(ignore, table, where, limit, transaction)
    }

    override fun queryLimit(size: Int, offset: Long, alreadyOrdered: Boolean): String {
        return (if (offset > 0) " OFFSET $offset ROWS" else "") + " FETCH FIRST $size ROWS ONLY"
    }
}

/**
 * Oracle dialect implementation.
 */
open class OracleDialect : VendorDialect(dialectName, OracleDataTypeProvider, OracleFunctionProvider) {
    override val supportsIfNotExists: Boolean = false
    override val needsSequenceToAutoInc: Boolean = true
    override val defaultReferenceOption: ReferenceOption = ReferenceOption.NO_ACTION
    override val needsQuotesWhenSymbolsInNames: Boolean = false
    override val supportsMultipleGeneratedKeys: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true
    override val supportsDualTableConcept: Boolean = true
    override val supportsOrderByNullsFirstLast: Boolean = true
    override val supportsOnUpdate: Boolean = false
    override val supportsSetDefaultReferenceOption: Boolean = false

    // Preventing the deletion of a parent row if a child row references it is the default behaviour in Oracle.
    override val supportsRestrictReferenceOption: Boolean = false

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return "DROP INDEX ${identifierManager.quoteIfNecessary(indexName)}"
    }

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> {
        val result = super.modifyColumn(column, columnDiff).map {
            it.replace("MODIFY COLUMN", "MODIFY")
        }
        return if (!columnDiff.nullability) {
            val nullableState = if (column.columnType.nullable) "NULL " else "NOT NULL"
            result.map {
                it.replace(nullableState, "")
            }
        } else {
            result
        }
    }

    override fun createDatabase(name: String): String = "CREATE DATABASE ${name.inProperCase()}"

    override fun listDatabases(): String = error("This operation is not supported by Oracle dialect")

    override fun dropDatabase(name: String): String = "DROP DATABASE"

    override fun setSchema(schema: Schema): String = "ALTER SESSION SET CURRENT_SCHEMA = ${schema.identifier}"

    override fun createSchema(schema: Schema): String = buildString {
        if ((schema.quota == null) xor (schema.on == null)) {
            @Suppress("UseRequire")
            throw IllegalArgumentException("You must either provide both <quota> and <on> options or non of them")
        }

        append("CREATE USER ", schema.identifier)
        append(" IDENTIFIED BY ", schema.password)
        appendIfNotNull(" DEFAULT TABLESPACE ", schema.defaultTablespace)
        appendIfNotNull(" TEMPORARY TABLESPACE ", schema.temporaryTablespace)
        appendIfNotNull(" QUOTA ", schema.quota)
        appendIfNotNull(" ON ", schema.on)
    }

    override fun dropSchema(schema: Schema, cascade: Boolean): String = buildString {
        append("DROP USER ", schema.identifier)

        if (cascade) {
            append(" CASCADE")
        }
    }

    /**
     * The SQL that gets the constraint information for Oracle returns a 1 for NO ACTION and does not support RESTRICT.
     * `decode (f.delete_rule, 'CASCADE', 0, 'SET NULL', 2, 1) as delete_rule`
     */
    override fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
        DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
        DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
        DatabaseMetaData.importedKeyRestrict -> ReferenceOption.NO_ACTION
        else -> currentDialect.defaultReferenceOption
    }

    companion object : DialectNameProvider("Oracle")
}
