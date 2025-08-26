package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.vendors.*
import java.math.BigDecimal

/**
 * Base class representing helper functions necessary for creating, altering, and dropping database schema objects.
 */
abstract class SchemaUtilityApi {
    /**
     * Returns this list of tables sorted according to the targets of their foreign key constraints, if any exist.
     * @suppress
     */
    @InternalApi
    protected fun Iterable<Table>.sortByReferences(): List<Table> = TableDepthGraph(this).sorted()

    /**
     * Whether any table from this list has a sequence of foreign key constraints that cycle back to them.
     * @suppress
     */
    @InternalApi
    protected fun List<Table>.hasCycle(): Boolean = TableDepthGraph(this).hasCycle()

    /**
     * Returns DDL for [table] without a sequence as a Pair of CREATE (includes its indexes) and ALTER statements.
     * @suppress
     */
    @InternalApi
    protected fun tableDdlWithoutExistingSequence(
        table: Table,
        existingSequence: Sequence?
    ): Pair<List<String>, List<String>> {
        val ddlWithoutExistingSequence = table.ddl.filter { statement ->
            if (existingSequence != null) {
                !statement.lowercase().startsWith("create sequence") ||
                    !statement.contains(existingSequence.name)
            } else {
                true
            }
        }.partition { it.startsWith("CREATE ") }
        val indicesDDL = table.indices.flatMap { it.createStatement() }
        return Pair(ddlWithoutExistingSequence.first + indicesDDL, ddlWithoutExistingSequence.second)
    }

    /**
     * Returns the SQL statements that create this [ForeignKeyConstraint].
     * @suppress
     */
    @InternalApi
    protected fun ForeignKeyConstraint.createDdl(): List<String> = with(this) {
        val allFromColumnsBelongsToTheSameTable = from.all { it.table == fromTable }
        require(allFromColumnsBelongsToTheSameTable) {
            "Not all referencing columns of $this belong to the same table"
        }
        val allTargetColumnsBelongToTheSameTable = target.all { it.table == targetTable }
        require(allTargetColumnsBelongToTheSameTable) {
            "Not all referenced columns of $this belong to the same table"
        }
        require(from.size == target.size) { "$this referencing columns are not in accordance with referenced" }
        require(deleteRule != null || updateRule != null) { "$this has no reference constraint actions" }
        require(target.toHashSet().size == target.size) { "Not all referenced columns of $this are unique" }
        return createStatement()
    }

    /**
     * Adds CREATE/ALTER statements for all table columns that don't exist in the database, to [destination].
     * @suppress
     */
    @InternalApi
    protected fun <C : MutableCollection<String>> Table.mapMissingColumnStatementsTo(
        destination: C,
        existingColumns: List<ColumnMetadata>,
        existingPrimaryKey: PrimaryKeyMetadata?,
        alterTableAddColumnSupported: Boolean,
        isIncorrectType: (columnMetadata: ColumnMetadata, column: Column<*>) -> Boolean
    ): C {
        // create columns
        val existingTableColumns = columns.mapNotNull { column ->
            val existingColumn = existingColumns.find { column.nameUnquoted().equals(it.name, true) }
            if (existingColumn != null) column to existingColumn else null
        }.toMap()
        val missingTableColumns = columns.filter { it !in existingTableColumns }
        missingTableColumns.flatMapTo(destination) { it.ddl }
        if (alterTableAddColumnSupported) {
            // create indexes with new columns
            indices.filter { index ->
                index.columns.any { missingTableColumns.contains(it) }
            }.forEach { destination.addAll(it.createStatement()) }
            // sync existing columns
            existingTableColumns
                .mapColumnDiffs(isIncorrectType)
                .flatMapTo(destination) { (col, changedState) ->
                    col.modifyStatements(changedState)
                }
            // add missing primary key
            primaryKeyDdl(missingTableColumns, existingPrimaryKey)?.let { destination.add(it) }
        }
        return destination
    }

    /**
     * Adds CREATE/ALTER/DROP statements for all foreign key constraints that don't exist in the database, to [destination].
     * @suppress
     */
    @InternalApi
    protected fun <C : MutableCollection<String>> mapMissingConstraintsTo(
        destination: C,
        allExistingConstraints: Map<Pair<Table, LinkedHashSet<Column<*>>>, List<ForeignKeyConstraint>>,
        vararg tables: Table
    ): C {
        val foreignKeyConstraints = tables.flatMap { table ->
            table.foreignKeys.map { it to allExistingConstraints[table to it.from]?.firstOrNull() }
        }
        for ((foreignKey, existingConstraint) in foreignKeyConstraints) {
            if (existingConstraint == null) {
                destination.addAll(foreignKey.createDdl())
                continue
            }
            val noForeignKey = existingConstraint.targetTable != foreignKey.targetTable
            val deleteRuleMismatch = foreignKey.deleteRule != existingConstraint.deleteRule
            val updateRuleMismatch = foreignKey.updateRule != existingConstraint.updateRule
            if (noForeignKey || deleteRuleMismatch || updateRuleMismatch) {
                destination.addAll(existingConstraint.dropStatement())
                destination.addAll(foreignKey.createDdl())
            }
        }
        return destination
    }

    /**
     * Filters all table indices and returns those that are defined on a table with more than one index.
     * If [withLogs] is `true`, DROP statements for these indices will also be logged.
     * @suppress
     */
    @InternalApi
    protected fun Map<Table, List<Index>>.filterAndLogExcessIndices(withLogs: Boolean): List<Index> {
        val excessiveIndices = flatMap { (_, indices) -> indices }
            .groupBy { index ->
                Triple(index.table, index.unique, index.columns.joinToString { column -> column.name })
            }
            .filterValues { it.size > 1 }
        if (excessiveIndices.isEmpty()) return emptyList()
        val toDrop = HashSet<Index>()
        if (withLogs) {
            exposedLogger.warn("List of excessive indices:")
            excessiveIndices.forEach { (triple, indices) ->
                val indexNames = indices.joinToString(", ") { index -> index.indexName }
                exposedLogger.warn("\t\t\t'${triple.first.tableName}'.'${triple.third}' -> $indexNames")
            }
            exposedLogger.info("SQL Queries to remove excessive indices:")
        }
        excessiveIndices.forEach { (_, indices) ->
            indices.take(indices.size - 1).forEach { index ->
                toDrop.add(index)
                if (withLogs) {
                    exposedLogger.info("\t\t\t${index.dropStatement()};")
                }
            }
        }
        return toDrop.toList()
    }

    /**
     * Filters all table foreign keys and returns those that are defined on a table with more than one of this constraint.
     * If [withLogs] is `true`, DROP statements for these constraints will also be logged.
     * @suppress
     */
    @InternalApi
    protected fun Map<Pair<Table, LinkedHashSet<Column<*>>>, List<ForeignKeyConstraint>>.filterAndLogExcessConstraints(
        withLogs: Boolean
    ): List<ForeignKeyConstraint> {
        val excessiveConstraints = filterValues { it.size > 1 }
        if (excessiveConstraints.isEmpty()) return emptyList()
        val toDrop = HashSet<ForeignKeyConstraint>()
        if (withLogs) {
            exposedLogger.warn("List of excessive foreign key constraints:")
            excessiveConstraints.forEach { (table, columns), fkConstraints ->
                val constraint = fkConstraints.first()
                val fkPartToLog = fkConstraints.joinToString(", ") { fkConstraint -> fkConstraint.fkName }
                exposedLogger.warn(
                    "\t\t\t'$table'.'$columns' -> '${constraint.fromTableName}':\t$fkPartToLog"
                )
            }
            exposedLogger.info("SQL Queries to remove excessive keys:")
        }
        excessiveConstraints.forEach { (_, fkConstraints) ->
            fkConstraints.take(fkConstraints.size - 1).forEach { fkConstraint ->
                toDrop.add(fkConstraint)
                if (withLogs) {
                    exposedLogger.info("\t\t\t${fkConstraint.dropStatement()};")
                }
            }
        }
        return toDrop.toList()
    }

    /**
     * Filters all table indices that are either missing from the database or exist in the database but are not mapped
     * in a table object. and returns those that are defined on a table with more than one of this constraint.
     * If [withLogs] is `true`, the corresponding statements for these indices will also be logged.
     *
     * @return Pair of CREATE statements for missing indices and, if [withDropIndices] is `true`, DROP statements ofr
     * unmapped indices; if [withDropIndices] is `false`, the second value will be an empty list.
     * @suppress
     */
    @InternalApi
    protected fun Map<Table, List<Index>>.filterAndLogMissingAndUnmappedIndices(
        existingFKConstraints: Set<Pair<Table, LinkedHashSet<Column<*>>>>,
        withDropIndices: Boolean,
        withLogs: Boolean,
        vararg tables: Table
    ): Pair<List<Index>, List<Index>> {
        fun List<Index>.filterForeignKeys() = if (currentDialect is MysqlDialect) {
            filterNot { it.table to LinkedHashSet(it.columns) in existingFKConstraints }
        } else {
            this
        }

        // SQLite: indices whose names start with "sqlite_" are meant for internal use
        fun List<Index>.filterInternalIndices() = if (currentDialect is SQLiteDialect) {
            filter { !it.indexName.startsWith("sqlite_") }
        } else {
            this
        }

        fun Table.existingIndices() = this@filterAndLogMissingAndUnmappedIndices[this].orEmpty()
            .filterForeignKeys()
            .filterInternalIndices()

        fun Table.mappedIndices() = this.indices.filterForeignKeys().filterInternalIndices()
        val missingIndices = HashSet<Index>()
        val unMappedIndices = HashMap<String, MutableSet<Index>>()
        val nameDiffers = HashSet<Index>()
        tables.forEach { table ->
            val existingTableIndices = table.existingIndices()
            val mappedIndices = table.mappedIndices()
            for (index in existingTableIndices) {
                val mappedIndex = mappedIndices.firstOrNull { it.onlyNameDiffer(index) } ?: continue
                if (withLogs) {
                    exposedLogger.info(
                        "Index on table '${table.tableName}' differs only in name: in db ${index.indexName} " +
                            "-> in mapping ${mappedIndex.indexName}"
                    )
                }
                nameDiffers.add(index)
                nameDiffers.add(mappedIndex)
            }
            unMappedIndices
                .getOrPut(table.nameInDatabaseCase()) { hashSetOf() }
                .addAll(existingTableIndices.subtract(mappedIndices))
            missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
        }
        val toCreate = missingIndices.subtract(nameDiffers)
        toCreate.log("Indices missed from database (will be created):", withLogs)
        val toDrop = mutableSetOf<Index>()
        unMappedIndices.forEach { (name, indices) ->
            indices.subtract(nameDiffers).also {
                if (withDropIndices) toDrop.addAll(it)
                it.log(
                    "Indices exist in database and not mapped in code on class '$name':",
                    withLogs
                )
            }
        }
        return Pair(toCreate.toList(), toDrop.toList())
    }

    /**
     * If [withLogs] is `true`, this logs every item in this collection, prefixed by [mainMessage].
     * @suppress
     */
    @InternalApi
    protected fun <T> Collection<T>.log(mainMessage: String, withLogs: Boolean) {
        if (withLogs && isNotEmpty()) {
            exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
        }
    }

    companion object {
        const val COLUMNS_LOG_MESSAGE = "Extracting table columns"

        const val PRIMARY_KEYS_LOG_MESSAGE = "Extracting primary keys"

        const val CONSTRAINTS_LOG_MESSAGE = "Extracting column constraints"

        const val CREATE_TABLES_LOG_MESSAGE = "Preparing create tables statements"

        const val EXECUTE_CREATE_TABLES_LOG_MESSAGE = "Executing create tables statements"

        const val CREATE_SEQUENCES_LOG_MESSAGE = "Preparing create sequences statements"

        const val ALTER_TABLES_LOG_MESSAGE = "Preparing alter tables statements"

        const val EXECUTE_ALTER_TABLES_LOG_MESSAGE = "Executing alter tables statements"

        const val MAPPING_CONSISTENCE_LOG_MESSAGE = "Checking mapping consistence"
    }

    @OptIn(InternalApi::class)
    private fun Map<Column<*>, ColumnMetadata>.mapColumnDiffs(
        isColumnTypeIncorrect: (columnMetadata: ColumnMetadata, column: Column<*>) -> Boolean
    ): Map<Column<*>, ColumnDiff> {
        val dialect = currentDialect
        return mapValues { (col, existingCol) ->
            val columnType = col.columnType
            val columnDbDefaultIsAllowed = col.dbDefaultValue?.let { dialect.isAllowedAsColumnDefault(it) }
            val colNullable = if (columnDbDefaultIsAllowed == false) {
                true // Treat a disallowed default value as null because that is what Exposed does with it
            } else {
                columnType.nullable
            }
            val incorrectType = if (currentDialect.supportsColumnTypeChange) {
                isColumnTypeIncorrect(existingCol, col)
            } else {
                false
            }
            val incorrectNullability = existingCol.nullable != colNullable
            val incorrectAutoInc = isIncorrectAutoInc(existingCol, col)
            // 'isDatabaseGenerated' property means that the column has generation of the value on the database side,
            // and it could be default value, trigger or something else,
            // but we don't specify the default value on the table object.
            // So it could be better to avoid checking for changes in defaults for such columns, because in the most part
            // of cases we would try to remove existing (in database, but not in table object) default value
            val incorrectDefaults = if (col.isDatabaseGenerated) false else isIncorrectDefault(dialect, existingCol, col, columnDbDefaultIsAllowed)
            val incorrectCaseSensitiveName = existingCol.name.inProperCase() != col.nameUnquoted().inProperCase()
            val incorrectSizeOrScale = if (incorrectType) false else isIncorrectSizeOrScale(existingCol, columnType)
            ColumnDiff(
                incorrectNullability,
                incorrectType,
                incorrectAutoInc,
                incorrectDefaults,
                incorrectCaseSensitiveName,
                incorrectSizeOrScale
            )
        }.filterValues { it.hasDifferences() }
    }

    private fun isIncorrectAutoInc(existingColumn: ColumnMetadata, column: Column<*>): Boolean {
        val isAutoIncColumn = column.columnType.isAutoInc
        return when {
            !existingColumn.autoIncrement && isAutoIncColumn && column.autoIncColumnType?.sequence == null -> true
            existingColumn.autoIncrement && isAutoIncColumn && column.autoIncColumnType?.sequence != null -> true
            existingColumn.autoIncrement && !isAutoIncColumn -> true
            else -> false
        }
    }

    private fun isIncorrectDefault(
        dialect: DatabaseDialect,
        existingColumn: ColumnMetadata,
        column: Column<*>,
        columnDbDefaultIsAllowed: Boolean?
    ): Boolean {
        val isExistingColumnDefaultNull = existingColumn.defaultDbValue == null
        val isDefinedColumnDefaultNull = columnDbDefaultIsAllowed != true ||
            (column.dbDefaultValue is LiteralOp<*> && (column.dbDefaultValue as? LiteralOp<*>)?.value == null)
        return when {
            // Both values are null-like, no DDL update is needed
            isExistingColumnDefaultNull && isDefinedColumnDefaultNull -> false
            // Only one of the values is null-like, DDL update is needed
            isExistingColumnDefaultNull != isDefinedColumnDefaultNull -> true
            else -> {
                val columnDefaultValue = column.dbDefaultValue?.let {
                    dialect.dbDefaultToString(column, it)
                }
                existingColumn.defaultDbValue != columnDefaultValue
            }
        }
    }

    @Suppress("NestedBlockDepth", "ComplexMethod", "LongMethod")
    private fun DatabaseDialect.dbDefaultToString(column: Column<*>, exp: Expression<*>): String {
        return when (exp) {
            is LiteralOp<*> -> {
                when (val value = exp.value) {
                    is Boolean -> when (this) {
                        is MysqlDialect -> if (value) "1" else "0"
                        is PostgreSQLDialect -> value.toString()
                        else -> dataTypeProvider.booleanToStatementString(value)
                    }
                    is String -> when {
                        this is PostgreSQLDialect -> when (column.columnType) {
                            is VarCharColumnType -> "'$value'::character varying"
                            is TextColumnType -> "'$value'::text"
                            else -> dataTypeProvider.processForDefaultValue(exp)
                        }
                        this is OracleDialect || h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> when {
                            column.columnType is VarCharColumnType && value == "" -> "NULL"
                            column.columnType is TextColumnType && value == "" -> "NULL"
                            else -> value
                        }
                        else -> value
                    }
                    is Enum<*> -> when (exp.columnType) {
                        is EnumerationNameColumnType<*> -> when (this) {
                            is PostgreSQLDialect -> "'${value.name}'::character varying"
                            else -> value.name
                        }
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is BigDecimal -> when (this) {
                        is MysqlDialect -> value.setScale((exp.columnType as DecimalColumnType).scale).toString()
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is Byte -> when {
                        this is PostgreSQLDialect && value < 0 -> "'${dataTypeProvider.processForDefaultValue(exp)}'::integer"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is Short -> when {
                        this is PostgreSQLDialect && value < 0 -> "'${dataTypeProvider.processForDefaultValue(exp)}'::integer"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is Int -> when {
                        this is PostgreSQLDialect && value < 0 -> "'${dataTypeProvider.processForDefaultValue(exp)}'::integer"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is Long -> when {
                        this is SQLServerDialect && (value < 0 || value > Int.MAX_VALUE.toLong()) ->
                            "${dataTypeProvider.processForDefaultValue(exp)}."
                        this is PostgreSQLDialect && (value < 0 || value > Int.MAX_VALUE.toLong()) ->
                            "'${dataTypeProvider.processForDefaultValue(exp)}'::bigint"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is UInt -> when {
                        this is SQLServerDialect && value > Int.MAX_VALUE.toUInt() -> "${dataTypeProvider.processForDefaultValue(exp)}."
                        this is PostgreSQLDialect && value > Int.MAX_VALUE.toUInt() -> "'${dataTypeProvider.processForDefaultValue(exp)}'::bigint"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    is ULong -> when {
                        this is SQLServerDialect && value > Int.MAX_VALUE.toULong() -> "${dataTypeProvider.processForDefaultValue(exp)}."
                        this is PostgreSQLDialect && value > Int.MAX_VALUE.toULong() -> "'${dataTypeProvider.processForDefaultValue(exp)}'::bigint"
                        else -> dataTypeProvider.processForDefaultValue(exp)
                    }
                    else -> {
                        when {
                            column.columnType is JsonColumnMarker -> {
                                val processed = dataTypeProvider.processForDefaultValue(exp)
                                when (this) {
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
                            column.columnType is ArrayColumnType<*, *> && this is PostgreSQLDialect -> {
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
                                    } ?: dataTypeProvider.processForDefaultValue(exp)
                            }
                            column.columnType is IDateColumnType -> {
                                val processed = dataTypeProvider.processForDefaultValue(exp)
                                if (processed.startsWith('\'') && processed.endsWith('\'')) {
                                    processed.trim('\'')
                                } else {
                                    processed
                                }
                            }
                            else -> dataTypeProvider.processForDefaultValue(exp)
                        }
                    }
                }
            }
            is Function<*> -> {
                var processed = dataTypeProvider.processForDefaultValue(exp)
                if (exp.columnType is IDateColumnType) {
                    if (processed.startsWith("CURRENT_TIMESTAMP") || processed == "GETDATE()") {
                        when (this) {
                            is SQLServerDialect -> processed = "getdate"
                            is MariaDBDialect -> processed = processed.lowercase()
                        }
                    }
                    if (processed.trim('(').startsWith("CURRENT_DATE")) {
                        when (this) {
                            is MysqlDialect -> processed = "curdate()"
                        }
                    }
                }
                processed
            }
            else -> dataTypeProvider.processForDefaultValue(exp)
        }
    }

    private fun isIncorrectSizeOrScale(columnMeta: ColumnMetadata, columnType: IColumnType<*>): Boolean {
        // ColumnMetadata.scale can only be non-null if ColumnMetadata.size is non-null
        if (columnMeta.size == null) return false
        val dialect = currentDialect
        return when (columnType) {
            is DecimalColumnType -> columnType.precision != columnMeta.size || columnType.scale != columnMeta.scale
            is CharColumnType -> columnType.colLength != columnMeta.size
            is VarCharColumnType -> columnType.colLength != columnMeta.size
            is BinaryColumnType -> if (dialect is PostgreSQLDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.PostgreSQL) {
                false
            } else {
                columnType.length != columnMeta.size
            }
            else -> false
        }
    }

    private fun Table.primaryKeyDdl(missingColumns: List<Column<*>>, existingKey: PrimaryKeyMetadata?): String? {
        val missingPK = primaryKey?.takeIf { pk ->
            pk.columns.none { it in missingColumns }
        }
        if (missingPK == null || existingKey != null) return null
        val missingPKName = missingPK.name.takeIf { isCustomPKNameDefined() }
        return currentDialect.addPrimaryKey(this, missingPKName, pkColumns = missingPK.columns)
    }

    /**
     * Runs the provided [block] and returns the result. If [withLogs] is `true`, logs the time taken in milliseconds.
     * @suppress
     */
    @InternalApi
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
}

/**
 * Utility functions that assist with creating, altering, and dropping table objects.
 *
 * None of the functions rely directly on the underlying driver.
 * @suppress
 */
@InternalApi
object TableUtils : SchemaUtilityApi() {
    /** Checks whether any of the [tables] have a sequence of foreign key constraints that cycle back to them. */
    internal fun checkCycle(vararg tables: Table) = tables.toList().hasCycle()

    /** Returns a list of [tables] sorted according to the targets of their foreign key constraints, if any exist. */
    fun sortTablesByReferences(tables: Iterable<Table>): List<Table> = tables.sortByReferences()
}
