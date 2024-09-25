package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.asLiteral
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal

/** Utility functions that assist with creating, altering, and dropping database schema objects. */
@Suppress("TooManyFunctions", "LargeClass")
object SchemaUtils {
    private inline fun <R> logTimeSpent(message: String, withLogs: Boolean, block: () -> R): R {
        return if (withLogs) {
            val start = System.currentTimeMillis()
            val answer = block()
            exposedLogger.info(message + " took " + (System.currentTimeMillis() - start) + "ms")
            answer
        } else {
            block()
        }
    }

    private class TableDepthGraph(val tables: Iterable<Table>) {
        val graph = fetchAllTables().let { tables ->
            if (tables.isEmpty()) {
                emptyMap()
            } else {
                tables.associateWith { t ->
                    t.foreignKeys.map { it.targetTable }
                }
            }
        }

        private fun fetchAllTables(): HashSet<Table> {
            val result = HashSet<Table>()

            fun parseTable(table: Table) {
                if (result.add(table)) {
                    table.foreignKeys.map { it.targetTable }.forEach(::parseTable)
                }
            }
            tables.forEach(::parseTable)
            return result
        }

        fun sorted(): List<Table> {
            if (!tables.iterator().hasNext()) return emptyList()

            val visited = mutableSetOf<Table>()
            val result = arrayListOf<Table>()

            fun traverse(table: Table) {
                if (table !in visited) {
                    visited += table
                    graph.getValue(table).forEach { t ->
                        if (t !in visited) {
                            traverse(t)
                        }
                    }
                    result += table
                }
            }

            tables.forEach(::traverse)
            return result
        }

        fun hasCycle(): Boolean {
            if (!tables.iterator().hasNext()) return false
            val visited = mutableSetOf<Table>()
            val recursion = mutableSetOf<Table>()

            val sortedTables = sorted()

            fun traverse(table: Table): Boolean {
                if (table in recursion) return true
                if (table in visited) return false
                recursion += table
                visited += table
                return if (graph[table]!!.any { traverse(it) }) {
                    true
                } else {
                    recursion -= table
                    false
                }
            }
            return sortedTables.any { traverse(it) }
        }
    }

    /** Returns a list of [tables] sorted according to the targets of their foreign key constraints, if any exist. */
    fun sortTablesByReferences(tables: Iterable<Table>): List<Table> = TableDepthGraph(tables).sorted()

    /** Checks whether any of the [tables] have a sequence of foreign key constraints that cycle back to them. */
    fun checkCycle(vararg tables: Table) = TableDepthGraph(tables.toList()).hasCycle()

    /** Returns the SQL statements that create all [tables] that do not already exist. */
    fun createStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()

        val toCreate = sortTablesByReferences(tables.toList()).filterNot { it.exists() }
        val alters = arrayListOf<String>()
        return toCreate.flatMap { table ->
            val (create, alter) = tableDdlWithoutExistingSequence(table).partition { it.startsWith("CREATE ") }
            val indicesDDL = table.indices.flatMap { createIndex(it) }
            alters += alter
            create + indicesDDL
        } + alters
    }

    private fun tableDdlWithoutExistingSequence(table: Table): List<String> {
        val existingAutoIncSeq = table.autoIncColumn?.autoIncColumnType?.sequence
            ?.takeIf { currentDialect.sequenceExists(it) }

        return table.ddl.filter { statement ->
            if (existingAutoIncSeq != null) {
                !statement.lowercase().startsWith("create sequence") || !statement.contains(existingAutoIncSeq.name)
            } else {
                true
            }
        }
    }

    /** Creates the provided sequences, using a batch execution if [inBatch] is set to `true`. */
    fun createSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = seq.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
        }
    }

    /** Drops the provided sequences, using a batch execution if [inBatch] is set to `true`. */
    fun dropSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val dropStatements = seq.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
        }
    }

    /** Returns the SQL statements that create the provided [ForeignKeyConstraint]. */
    fun createFKey(foreignKey: ForeignKeyConstraint): List<String> = with(foreignKey) {
        val allFromColumnsBelongsToTheSameTable = from.all { it.table == fromTable }
        require(
            allFromColumnsBelongsToTheSameTable
        ) { "not all referencing columns of $foreignKey belong to the same table" }
        val allTargetColumnsBelongToTheSameTable = target.all { it.table == targetTable }
        require(
            allTargetColumnsBelongToTheSameTable
        ) { "not all referenced columns of $foreignKey belong to the same table" }
        require(from.size == target.size) { "$foreignKey referencing columns are not in accordance with referenced" }
        require(deleteRule != null || updateRule != null) { "$foreignKey has no reference constraint actions" }
        require(target.toHashSet().size == target.size) { "not all referenced columns of $foreignKey are unique" }

        return createStatement()
    }

    /** Returns the SQL statements that create the provided [index]. */
    fun createIndex(index: Index): List<String> = index.createStatement()

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

    /**
     * Returns the SQL statements that create any columns defined in [tables], which are missing from the existing
     * tables in the database.
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * **Note:** Some dialects, like SQLite, do not support `ALTER TABLE ADD COLUMN` syntax completely.
     * Please check the documentation.
     */
    fun addMissingColumnsStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = ArrayList<String>()

        val existingTablesColumns = logTimeSpent("Extracting table columns", withLogs) {
            currentDialect.tableColumns(*tables)
        }

        val existingPrimaryKeys = logTimeSpent("Extracting primary keys", withLogs) {
            currentDialect.existingPrimaryKeys(*tables)
        }

        val dbSupportsAlterTableWithAddColumn = TransactionManager.current().db.supportsAlterTableWithAddColumn

        for (table in tables) {
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

    private fun Transaction.execStatements(inBatch: Boolean, statements: List<String>) {
        if (inBatch) {
            execInBatch(statements)
        } else {
            for (statement in statements) {
                exec(statement)
            }
        }
    }

    /** Creates all [tables] that do not already exist, using a batch execution if [inBatch] is set to `true`. */
    fun <T : Table> create(vararg tables: T, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            execStatements(inBatch, createStatements(*tables))
            commit()
            currentDialect.resetCaches()
        }
    }

    /**
     * Creates databases
     *
     * @param databases the names of the databases
     * @param inBatch flag to perform database creation in a single batch
     *
     * For PostgreSQL, calls to this function should be preceded by connection.autoCommit = true,
     * and followed by connection.autoCommit = false.
     * @see org.jetbrains.exposed.sql.tests.shared.ddl.CreateDatabaseTest
     */
    fun createDatabase(vararg databases: String, inBatch: Boolean = false) {
        val transaction = TransactionManager.current()
        try {
            with(transaction) {
                val createStatements = databases.flatMap { listOf(currentDialect.createDatabase(it)) }
                execStatements(inBatch, createStatements)
            }
        } catch (exception: ExposedSQLException) {
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.autoCommit) {
                throw IllegalStateException(
                    "${currentDialect.name} requires autoCommit to be enabled for CREATE DATABASE",
                    exception
                )
            } else {
                throw exception
            }
        }
    }

    /**
     * Returns a list of all databases.
     *
     * @return A list of strings representing the names of all databases.
     */
    fun listDatabases(): List<String> {
        val transaction = TransactionManager.current()
        return with(transaction) {
            exec(currentDialect.listDatabases()) {
                val result = mutableListOf<String>()
                while (it.next()) {
                    result.add(it.getString(1).lowercase())
                }
                result
            } ?: emptyList()
        }
    }

    /**
     * Drops databases
     *
     * @param databases the names of the databases
     * @param inBatch flag to perform database creation in a single batch
     *
     * For PostgreSQL, calls to this function should be preceded by connection.autoCommit = true,
     * and followed by connection.autoCommit = false.
     * @see org.jetbrains.exposed.sql.tests.shared.ddl.CreateDatabaseTest
     */
    fun dropDatabase(vararg databases: String, inBatch: Boolean = false) {
        val transaction = TransactionManager.current()
        try {
            with(transaction) {
                val createStatements = databases.flatMap { listOf(currentDialect.dropDatabase(it)) }
                execStatements(inBatch, createStatements)
            }
        } catch (exception: ExposedSQLException) {
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.autoCommit) {
                throw IllegalStateException(
                    "${currentDialect.name} requires autoCommit to be enabled for DROP DATABASE",
                    exception
                )
            } else {
                throw exception
            }
        }
    }

    /**
     * This function should be used in cases when an easy-to-use auto-actualization of database schema is required.
     * It creates any missing tables and, if possible, adds any missing columns for existing tables
     * (for example, when columns are nullable or have default values).
     *
     * **Note:** Some dialects, like SQLite, do not support `ALTER TABLE ADD COLUMN` syntax completely,
     * which restricts the behavior when adding some missing columns. Please check the documentation.
     *
     * Also, if there is inconsistency between the database schema and table objects (for example,
     * excessive or missing indices), then SQL statements to fix this will be logged at the INFO level.
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * **Note:** This functionality is reliant on retrieving JDBC metadata, which might be a bit slow. It is recommended
     * to call this function only once at application startup and to provide all tables that need to be actualized.
     *
     * **Note:** Execution of this function concurrently might lead to unpredictable state in the database due to
     * non-transactional behavior of some DBMS when processing DDL statements (for example, MySQL) and metadata caches.
     * To prevent such cases, it is advised to use any preferred "global" synchronization (via redis or memcached) or
     * to use a lock based on synchronization with a dummy table.
     * @see SchemaUtils.withDataBaseLock
     */
    fun createMissingTablesAndColumns(vararg tables: Table, inBatch: Boolean = false, withLogs: Boolean = true) {
        with(TransactionManager.current()) {
            db.dialect.resetCaches()
            val createStatements = logTimeSpent("Preparing create tables statements", withLogs) {
                createStatements(*tables)
            }
            logTimeSpent("Executing create tables statements", withLogs) {
                execStatements(inBatch, createStatements)
                commit()
            }

            val alterStatements = logTimeSpent("Preparing alter table statements", withLogs) {
                addMissingColumnsStatements(tables = tables, withLogs)
            }
            logTimeSpent("Executing alter table statements", withLogs) {
                execStatements(inBatch, alterStatements)
                commit()
            }
            val executedStatements = createStatements + alterStatements
            logTimeSpent("Checking mapping consistence", withLogs) {
                val modifyTablesStatements = checkMappingConsistence(
                    tables = tables,
                    withLogs
                ).filter { it !in executedStatements }
                execStatements(inBatch, modifyTablesStatements)
                commit()
            }
            db.dialect.resetCaches()
        }
    }

    /**
     * Returns the SQL statements that need to be executed to make the existing database schema compatible with
     * the table objects defined using Exposed.
     *
     * **Note:** Some dialects, like SQLite, do not support `ALTER TABLE ADD COLUMN` syntax completely,
     * which restricts the behavior when adding some missing columns. Please check the documentation.
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     */
    fun statementsRequiredToActualizeScheme(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val (tablesToCreate, tablesToAlter) = tables.partition { !it.exists() }
        val createStatements = logTimeSpent("Preparing create tables statements", withLogs) {
            createStatements(tables = tablesToCreate.toTypedArray())
        }
        val alterStatements = logTimeSpent("Preparing alter table statements", withLogs) {
            addMissingColumnsStatements(tables = tablesToAlter.toTypedArray(), withLogs)
        }
        val executedStatements = createStatements + alterStatements
        val modifyTablesStatements = logTimeSpent("Checking mapping consistence", withLogs) {
            checkMappingConsistence(
                tables = tablesToAlter.toTypedArray(),
                withLogs
            ).filter { it !in executedStatements }
        }
        return executedStatements + modifyTablesStatements
    }

    /**
     * Log Exposed table mappings <-> real database mapping problems and returns DDL Statements to fix them
     */
    fun checkMappingConsistence(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (withLogs) {
            checkExcessiveForeignKeyConstraints(tables = tables, withLogs = true)
            checkExcessiveIndices(tables = tables, withLogs = true)
        }
        return checkMissingAndUnmappedIndices(tables = tables, withLogs).flatMap { it.createStatement() }
    }

    /**
     * Checks all [tables] for any that have more than one defined index and logs the findings. If found, this function
     * also logs the SQL statements that can be used to drop these indices.
     *
     * @return List of indices that are excessive and can be dropped.
     */
    @Suppress("NestedBlockDepth")
    fun checkExcessiveIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        val excessiveIndices =
            currentDialect.existingIndices(*tables).flatMap { (_, indices) ->
                indices
            }.groupBy { index -> Triple(index.table, index.unique, index.columns.joinToString { column -> column.name }) }
                .filter { (_, indices) -> indices.size > 1 }

        return if (excessiveIndices.isEmpty()) {
            emptyList()
        } else {
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

            toDrop.toList()
        }
    }

    /**
     * Checks all [tables] for any that have more than one defined foreign key constraint and logs the findings. If
     * found, this function also logs the SQL statements that can be used to drop these foreign key constraints.
     *
     * @return List of foreign key constraints that are excessive and can be dropped.
     */
    @Suppress("NestedBlockDepth")
    fun checkExcessiveForeignKeyConstraints(vararg tables: Table, withLogs: Boolean): List<ForeignKeyConstraint> {
        val excessiveConstraints = currentDialect.columnConstraints(*tables).filter { (_, fkConstraints) -> fkConstraints.size > 1 }

        return if (excessiveConstraints.isEmpty()) {
            emptyList()
        } else {
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

            toDrop.toList()
        }
    }

    /**
     * Checks all [tables] for any that have indices that are missing in the database but are defined in the code. If
     * found, this function also logs the SQL statements that can be used to create these indices.
     * Checks all [tables] for any that have indices that exist in the database but are not mapped in the code. If
     * found, this function only logs the SQL statements that can be used to drop these indices, but does not include
     * them in the returned list.
     *
     * @return List of indices that are missing and can be created.
     */
    private fun checkMissingAndUnmappedIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        fun Collection<Index>.log(mainMessage: String) {
            if (withLogs && isNotEmpty()) {
                exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
            }
        }

        val foreignKeyConstraints = currentDialect.columnConstraints(*tables).keys
        val existingIndices = currentDialect.existingIndices(*tables)

        fun List<Index>.filterForeignKeys() = if (currentDialect is MysqlDialect) {
            filterNot { it.table to LinkedHashSet(it.columns) in foreignKeyConstraints }
        } else {
            this
        }

        // SQLite: indices whose names start with "sqlite_" are meant for internal use
        fun List<Index>.filterInternalIndices() = if (currentDialect is SQLiteDialect) {
            filter { !it.indexName.startsWith("sqlite_") }
        } else {
            this
        }

        fun Table.existingIndices() = existingIndices[this].orEmpty().filterForeignKeys().filterInternalIndices()

        fun Table.mappedIndices() = this.indices.filterForeignKeys().filterInternalIndices()

        val missingIndices = HashSet<Index>()
        val notMappedIndices = HashMap<String, MutableSet<Index>>()
        val nameDiffers = HashSet<Index>()

        tables.forEach { table ->
            val existingTableIndices = table.existingIndices()
            val mappedIndices = table.mappedIndices()

            for (index in existingTableIndices) {
                val mappedIndex = mappedIndices.firstOrNull { it.onlyNameDiffer(index) } ?: continue
                if (withLogs) {
                    exposedLogger.info(
                        "Index on table '${table.tableName}' differs only in name: in db ${index.indexName} -> in mapping ${mappedIndex.indexName}"
                    )
                }
                nameDiffers.add(index)
                nameDiffers.add(mappedIndex)
            }

            notMappedIndices.getOrPut(table.nameInDatabaseCase()) {
                hashSetOf()
            }.addAll(existingTableIndices.subtract(mappedIndices))

            missingIndices.addAll(mappedIndices.subtract(existingTableIndices))
        }

        val toCreate = missingIndices.subtract(nameDiffers)
        toCreate.log("Indices missed from database (will be created):")
        notMappedIndices.forEach { (name, indexes) ->
            indexes.subtract(nameDiffers).log("Indices exist in database and not mapped in code on class '$name':")
        }
        return toCreate.toList()
    }

    /**
     * Creates table with name "busy" (if not present) and single column to be used as "synchronization" point. Table wont be dropped after execution.
     *
     * All code provided in _body_ closure will be executed only if there is no another code which running under "withDataBaseLock" at same time.
     * That means that concurrent execution of long running tasks under "database lock" might lead to that only first of them will be really executed.
     */
    fun <T> Transaction.withDataBaseLock(body: () -> T) {
        val buzyTable = object : Table("busy") {
            val busy = bool("busy").uniqueIndex()
        }
        create(buzyTable)
        val isBusy = buzyTable.selectAll().forUpdate().any()
        if (!isBusy) {
            buzyTable.insert { it[buzyTable.busy] = true }
            try {
                body()
            } finally {
                buzyTable.deleteAll()
                connection.commit()
            }
        }
    }

    /**
     * Retrieves a list of all table names in the current database.
     *
     * @return A list of table names as strings.
     */
    fun listTables(): List<String> = currentDialect.allTablesNames()

    /** Drops all [tables], using a batch execution if [inBatch] is set to `true`. */
    fun drop(vararg tables: Table, inBatch: Boolean = false) {
        if (tables.isEmpty()) return
        with(TransactionManager.current()) {
            var tablesForDeletion = sortTablesByReferences(tables.toList()).reversed().filter { it in tables }
            if (!currentDialect.supportsIfNotExists) {
                tablesForDeletion = tablesForDeletion.filter { it.exists() }
            }
            val dropStatements = tablesForDeletion.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
            currentDialect.resetCaches()
        }
    }

    /**
     * Sets the current default schema to [schema]. Supported by H2, MariaDB, Mysql, Oracle, PostgreSQL and SQL Server.
     * SQLite doesn't support schemas.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests
     */
    fun setSchema(schema: Schema, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = schema.setSchemaStatement()

            execStatements(inBatch, createStatements)

            when (currentDialect) {
                /** Sets manually the database name in connection.catalog for Mysql.
                 * Mysql doesn't change catalog after executing "Use db" statement*/
                is MysqlDialect -> {
                    connection.catalog = schema.identifier
                }

                is H2Dialect -> {
                    connection.schema = schema.identifier
                }
            }
            currentDialect.resetCaches()
            connection.metadata { resetCurrentScheme() }
        }
    }

    /**
     * Creates schemas
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests
     *
     * @param schemas the names of the schemas
     * @param inBatch flag to perform schema creation in a single batch
     */
    fun createSchema(vararg schemas: Schema, inBatch: Boolean = false) {
        if (schemas.isEmpty()) return
        with(TransactionManager.current()) {
            val toCreate = schemas.distinct().filterNot { it.exists() }
            val createStatements = toCreate.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
            commit()
            currentDialect.resetSchemaCaches()
        }
    }

    /**
     * Drops schemas
     *
     * **Note** that when you are using Mysql or MariaDB, this will fail if you try to drop a schema that
     * contains a table that is referenced by a table in another schema.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests.testDropSchemaWithCascade
     *
     * @param schemas the names of the schema
     * @param cascade flag to drop schema and all of its objects and all objects that depend on those objects.
     * **Note** This option is not supported by MySQL, MariaDB, or SQL Server, so all objects in the schema will be
     * dropped regardless of the flag's value.
     * @param inBatch flag to perform schema creation in a single batch
     */
    fun dropSchema(vararg schemas: Schema, cascade: Boolean = false, inBatch: Boolean = false) {
        if (schemas.isEmpty()) return
        with(TransactionManager.current()) {
            val schemasForDeletion = if (currentDialect.supportsIfNotExists) {
                schemas.distinct()
            } else {
                schemas.distinct().filter { it.exists() }
            }
            val dropStatements = schemasForDeletion.flatMap { it.dropStatement(cascade) }

            execStatements(inBatch, dropStatements)

            currentDialect.resetSchemaCaches()
        }
    }
}
