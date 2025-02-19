package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.SchemaUtils.createFKey
import org.jetbrains.exposed.sql.SchemaUtils.createIndex
import org.jetbrains.exposed.sql.SqlExpressionBuilder.asLiteral
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal

/** Base class housing shared code between [SchemaUtils] and [MigrationUtils]. */
abstract class BaseSchemaUtils {
    protected inline fun <R> logTimeSpent(message: String, withLogs: Boolean, block: () -> R): R {
        return if (withLogs) {
            val start = System.currentTimeMillis()
            val answer = block()
            exposedLogger.info(message + " took " + (System.currentTimeMillis() - start) + "ms")
            answer
        } else {
            block()
        }
    }

    protected fun addMissingColumnsStatements(vararg tables: Table, existingTablesColumns: Map<Table, List<ColumnMetadata>>, withLogs: Boolean = true): List<String> {
        val statements = ArrayList<String>()

        val existingPrimaryKeys = logTimeSpent("Extracting primary keys", withLogs) {
            currentDialect.existingPrimaryKeys(*tables)
        }

        val dbSupportsAlterTableWithAddColumn = TransactionManager.current().db.supportsAlterTableWithAddColumn

        tables.forEach { table ->
            // create columns
            val thisTableExistingColumns = existingTablesColumns[table].orEmpty()
            val existingTableColumns = table.columns.mapNotNull { column ->
                val existingColumn = thisTableExistingColumns.find { column.nameUnquoted().equals(it.name, true) }
                if (existingColumn != null) column to existingColumn else null
            }.toMap()
            val missingTableColumns = table.columns.filter { it !in existingTableColumns }

            missingTableColumns.flatMapTo(statements) { it.ddl }

            if (dbSupportsAlterTableWithAddColumn) {
                // create indexes with new columns
                table.indices.filter { index ->
                    index.columns.any {
                        missingTableColumns.contains(it)
                    }
                }.forEach { statements.addAll(createIndex(it)) }

                // sync existing columns
                val dataTypeProvider = currentDialect.dataTypeProvider
                val redoColumns = existingTableColumns.mapValues { (col, existingCol) ->
                    val columnType = col.columnType
                    val colNullable = if (col.dbDefaultValue?.let { currentDialect.isAllowedAsColumnDefault(it) } == false) {
                        true // Treat a disallowed default value as null because that is what Exposed does with it
                    } else {
                        columnType.nullable
                    }
                    val incorrectNullability = existingCol.nullable != colNullable

                    val incorrectAutoInc = isIncorrectAutoInc(existingCol, col)

                    val incorrectDefaults = isIncorrectDefault(dataTypeProvider, existingCol, col)

                    val incorrectCaseSensitiveName = existingCol.name.inProperCase() != col.nameUnquoted().inProperCase()

                    val incorrectSizeOrScale = isIncorrectSizeOrScale(existingCol, columnType)

                    ColumnDiff(incorrectNullability, incorrectAutoInc, incorrectDefaults, incorrectCaseSensitiveName, incorrectSizeOrScale)
                }.filterValues { it.hasDifferences() }

                redoColumns.flatMapTo(statements) { (col, changedState) -> col.modifyStatements(changedState) }

                // add missing primary key
                val missingPK = table.primaryKey?.takeIf { pk -> pk.columns.none { it in missingTableColumns } }
                if (missingPK != null && existingPrimaryKeys[table] == null) {
                    val missingPKName = missingPK.name.takeIf { table.isCustomPKNameDefined() }
                    statements.add(
                        currentDialect.addPrimaryKey(table, missingPKName, pkColumns = missingPK.columns)
                    )
                }
            }
        }

        if (dbSupportsAlterTableWithAddColumn) {
            statements.addAll(addMissingColumnConstraints(*tables, withLogs = withLogs))
        }

        return statements
    }

    private fun isIncorrectAutoInc(columnMetadata: ColumnMetadata, column: Column<*>): Boolean = when {
        !columnMetadata.autoIncrement && column.columnType.isAutoInc && column.autoIncColumnType?.sequence == null ->
            true
        columnMetadata.autoIncrement && column.columnType.isAutoInc && column.autoIncColumnType?.sequence != null ->
            true
        columnMetadata.autoIncrement && !column.columnType.isAutoInc -> true
        else -> false
    }

    /**
     * For DDL purposes we do not segregate the cases when the default value was not specified, and when it
     * was explicitly set to `null`.
     */
    private fun isIncorrectDefault(dataTypeProvider: DataTypeProvider, columnMeta: ColumnMetadata, column: Column<*>): Boolean {
        val isExistingColumnDefaultNull = columnMeta.defaultDbValue == null
        val isDefinedColumnDefaultNull = column.dbDefaultValue?.takeIf { currentDialect.isAllowedAsColumnDefault(it) } == null ||
            (column.dbDefaultValue is LiteralOp<*> && (column.dbDefaultValue as? LiteralOp<*>)?.value == null)

        return when {
            // Both values are null-like, no DDL update is needed
            isExistingColumnDefaultNull && isDefinedColumnDefaultNull -> false
            // Only one of the values is null-like, DDL update is needed
            isExistingColumnDefaultNull != isDefinedColumnDefaultNull -> true

            else -> {
                val columnDefaultValue = column.dbDefaultValue?.let {
                    dataTypeProvider.dbDefaultToString(column, it)
                }
                columnMeta.defaultDbValue != columnDefaultValue
            }
        }
    }

    private fun isIncorrectSizeOrScale(columnMeta: ColumnMetadata, columnType: IColumnType<*>): Boolean {
        // ColumnMetadata.scale can only be non-null if ColumnMetadata.size is non-null
        if (columnMeta.size == null) return false

        return when (columnType) {
            is DecimalColumnType -> columnType.precision != columnMeta.size || columnType.scale != columnMeta.scale
            is CharColumnType -> columnType.colLength != columnMeta.size
            is VarCharColumnType -> columnType.colLength != columnMeta.size
            is BinaryColumnType -> columnType.length != columnMeta.size
            else -> false
        }
    }

    private fun addMissingColumnConstraints(vararg tables: Table, withLogs: Boolean): List<String> {
        val existingColumnConstraint = logTimeSpent("Extracting column constraints", withLogs) {
            currentDialect.columnConstraints(*tables)
        }

        val foreignKeyConstraints = tables.flatMap { table ->
            table.foreignKeys.map { it to existingColumnConstraint[table to it.from]?.firstOrNull() }
        }

        val statements = ArrayList<String>()

        for ((foreignKey, existingConstraint) in foreignKeyConstraints) {
            if (existingConstraint == null) {
                statements.addAll(createFKey(foreignKey))
                continue
            }

            val noForeignKey = existingConstraint.targetTable != foreignKey.targetTable
            val deleteRuleMismatch = foreignKey.deleteRule != existingConstraint.deleteRule
            val updateRuleMismatch = foreignKey.updateRule != existingConstraint.updateRule

            if (noForeignKey || deleteRuleMismatch || updateRuleMismatch) {
                statements.addAll(existingConstraint.dropStatement())
                statements.addAll(createFKey(foreignKey))
            }
        }

        return statements
    }

    @Suppress("NestedBlockDepth", "ComplexMethod", "LongMethod")
    private fun DataTypeProvider.dbDefaultToString(column: Column<*>, exp: Expression<*>): String {
        return when (exp) {
            is LiteralOp<*> -> {
                val dialect = currentDialect
                when (val value = exp.value) {
                    is Boolean -> when (dialect) {
                        is MysqlDialect -> if (value) "1" else "0"
                        is PostgreSQLDialect -> value.toString()
                        else -> booleanToStatementString(value)
                    }

                    is String -> when {
                        dialect is PostgreSQLDialect -> when (column.columnType) {
                            is VarCharColumnType -> "'$value'::character varying"
                            is TextColumnType -> "'$value'::text"
                            else -> processForDefaultValue(exp)
                        }

                        dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> when {
                            column.columnType is VarCharColumnType && value == "" -> "NULL"
                            column.columnType is TextColumnType && value == "" -> "NULL"
                            else -> value
                        }

                        else -> value
                    }

                    is Enum<*> -> when (exp.columnType) {
                        is EnumerationNameColumnType<*> -> when (dialect) {
                            is PostgreSQLDialect -> "'${value.name}'::character varying"
                            else -> value.name
                        }

                        else -> processForDefaultValue(exp)
                    }

                    is BigDecimal -> when (dialect) {
                        is MysqlDialect -> value.setScale((exp.columnType as DecimalColumnType).scale).toString()
                        else -> processForDefaultValue(exp)
                    }

                    is Byte -> when {
                        dialect is PostgreSQLDialect && value < 0 -> "'${processForDefaultValue(exp)}'::integer"
                        else -> processForDefaultValue(exp)
                    }

                    is Short -> when {
                        dialect is PostgreSQLDialect && value < 0 -> "'${processForDefaultValue(exp)}'::integer"
                        else -> processForDefaultValue(exp)
                    }

                    is Int -> when {
                        dialect is PostgreSQLDialect && value < 0 -> "'${processForDefaultValue(exp)}'::integer"
                        else -> processForDefaultValue(exp)
                    }

                    is Long -> when {
                        currentDialect is SQLServerDialect && (value < 0 || value > Int.MAX_VALUE.toLong()) ->
                            "${processForDefaultValue(exp)}."
                        currentDialect is PostgreSQLDialect && (value < 0 || value > Int.MAX_VALUE.toLong()) ->
                            "'${processForDefaultValue(exp)}'::bigint"
                        else -> processForDefaultValue(exp)
                    }

                    is UInt -> when {
                        dialect is SQLServerDialect && value > Int.MAX_VALUE.toUInt() -> "${processForDefaultValue(exp)}."
                        dialect is PostgreSQLDialect && value > Int.MAX_VALUE.toUInt() -> "'${processForDefaultValue(exp)}'::bigint"
                        else -> processForDefaultValue(exp)
                    }

                    is ULong -> when {
                        currentDialect is SQLServerDialect && value > Int.MAX_VALUE.toULong() -> "${processForDefaultValue(exp)}."
                        currentDialect is PostgreSQLDialect && value > Int.MAX_VALUE.toULong() -> "'${processForDefaultValue(exp)}'::bigint"
                        else -> processForDefaultValue(exp)
                    }

                    else -> {
                        when {
                            column.columnType is JsonColumnMarker -> {
                                val processed = processForDefaultValue(exp)
                                when (dialect) {
                                    is PostgreSQLDialect -> {
                                        if (column.columnType.usesBinaryFormat) {
                                            processed.replace(Regex("(\"|})(:|,)(\\[|\\{|\")"), "$1$2 $3")
                                        } else {
                                            processed
                                        }
                                    }

                                    is MariaDBDialect -> processed.trim('\'')
                                    is MysqlDialect -> "_utf8mb4\\'${processed.trim('(', ')', '\'')}\\'"
                                    else -> when {
                                        processed.startsWith('\'') && processed.endsWith('\'') -> processed.trim('\'')
                                        else -> processed
                                    }
                                }
                            }

                            column.columnType is ArrayColumnType<*, *> && dialect is PostgreSQLDialect -> {
                                (value as List<*>)
                                    .takeIf { it.isNotEmpty() }
                                    ?.run {
                                        val delegateColumnType = column.columnType.delegate as IColumnType<Any>
                                        val delegateColumn = (column as Column<Any?>).withColumnType(delegateColumnType)
                                        val processed = map {
                                            if (delegateColumn.columnType is StringColumnType) {
                                                "'$it'::text"
                                            } else {
                                                dbDefaultToString(delegateColumn, delegateColumn.asLiteral(it))
                                            }
                                        }
                                        "ARRAY$processed"
                                    } ?: processForDefaultValue(exp)
                            }

                            column.columnType is IDateColumnType -> {
                                val processed = processForDefaultValue(exp)
                                if (processed.startsWith('\'') && processed.endsWith('\'')) {
                                    processed.trim('\'')
                                } else {
                                    processed
                                }
                            }

                            else -> processForDefaultValue(exp)
                        }
                    }
                }
            }

            is Function<*> -> {
                var processed = processForDefaultValue(exp)
                if (exp.columnType is IDateColumnType) {
                    if (processed.startsWith("CURRENT_TIMESTAMP") || processed == "GETDATE()") {
                        when (currentDialect) {
                            is SQLServerDialect -> processed = "getdate"
                            is MariaDBDialect -> processed = processed.lowercase()
                        }
                    }
                    if (processed.trim('(').startsWith("CURRENT_DATE")) {
                        when (currentDialect) {
                            is MysqlDialect -> processed = "curdate()"
                        }
                    }
                }
                processed
            }

            else -> processForDefaultValue(exp)
        }
    }
}
