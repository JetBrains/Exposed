package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal

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
            if (tables.isEmpty()) emptyMap()
            else {
                tables.associateWith { t ->
                    t.columns.mapNotNull { c ->
                        c.referee?.let { it.table to c.columnType.nullable }
                    }.toMap()
                }
            }
        }

        private fun fetchAllTables(): HashSet<Table> {
            val result = HashSet<Table>()

            fun parseTable(table: Table) {
                if (result.add(table)) {
                    table.columns.forEach {
                        it.referee?.table?.let(::parseTable)
                    }
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
                    graph.getValue(table).forEach { (t, _) ->
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
                return if (graph[table]!!.any { traverse(it.key) }) {
                    true
                } else {
                    recursion -= table
                    false
                }
            }
            return sortedTables.any { traverse(it) }
        }
    }

    fun sortTablesByReferences(tables: Iterable<Table>) = TableDepthGraph(tables).sorted()
    fun checkCycle(vararg tables: Table) = TableDepthGraph(tables.toList()).hasCycle()

    fun createStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()

        val toCreate = sortTablesByReferences(tables.toList()).filterNot { it.exists() }
        val alters = arrayListOf<String>()
        return toCreate.flatMap { table ->
            val (create, alter) = table.ddl.partition { it.startsWith("CREATE ") }
            val indicesDDL = table.indices.flatMap { createIndex(it) }
            alters += alter
            create + indicesDDL
        } + alters
    }

    fun createSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = seq.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
        }
    }

    fun dropSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val dropStatements = seq.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
        }
    }

    @Deprecated(
        "Will be removed in upcoming releases. Please use overloaded version instead",
        ReplaceWith("createFKey(checkNotNull(reference.foreignKey) { \"${"$"}reference does not reference anything\" })"),
        DeprecationLevel.ERROR
    )
    fun createFKey(reference: Column<*>): List<String> {
        val foreignKey = reference.foreignKey
        require(foreignKey != null && (foreignKey.deleteRule != null || foreignKey.updateRule != null)) { "$reference does not reference anything" }
        return createFKey(foreignKey)
    }

    fun createFKey(foreignKey: ForeignKeyConstraint): List<String> = with(foreignKey) {
        val allFromColumnsBelongsToTheSameTable = from.all { it.table == fromTable }
        require(allFromColumnsBelongsToTheSameTable) { "not all referencing columns of $foreignKey belong to the same table" }
        val allTargetColumnsBelongToTheSameTable = target.all { it.table == targetTable }
        require(allTargetColumnsBelongToTheSameTable) { "not all referenced columns of $foreignKey belong to the same table" }
        require(from.size == target.size) { "$foreignKey referencing columns are not in accordance with referenced" }
        require(deleteRule != null || updateRule != null) { "$foreignKey has no reference constraint actions" }
        require(target.toHashSet().size == target.size) { "not all referenced columns of $foreignKey are unique" }

        return createStatement()
    }

    fun createIndex(index: Index) = index.createStatement()

    @Suppress("NestedBlockDepth", "ComplexMethod")
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
                        dialect is PostgreSQLDialect ->
                            when(column.columnType) {
                                is VarCharColumnType -> "'${value}'::character varying"
                                is TextColumnType -> "'${value}'::text"
                                else -> processForDefaultValue(exp)
                            }
                        dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                            when {
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
                    else -> processForDefaultValue(exp)
                }
            }
            else -> processForDefaultValue(exp)
        }
    }

    fun addMissingColumnsStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = ArrayList<String>()

        val existingTablesColumns = logTimeSpent("Extracting table columns", withLogs) {
            currentDialect.tableColumns(*tables)
        }

        val dbSupportsAlterTableWithAddColumn = TransactionManager.current().db.supportsAlterTableWithAddColumn

        for (table in tables) {
            // create columns
            val thisTableExistingColumns = existingTablesColumns[table].orEmpty()
            val existingTableColumns = table.columns.mapNotNull { column ->
                val existingColumn = thisTableExistingColumns.find { column.name.equals(it.name, true) }
                if (existingColumn != null) column to existingColumn else null
            }.toMap()
            val missingTableColumns = table.columns.filter { it !in existingTableColumns }

            missingTableColumns.flatMapTo(statements) { it.ddl }

            if (dbSupportsAlterTableWithAddColumn) {
                // create indexes with new columns
                table.indices
                    .filter { index -> index.columns.any { missingTableColumns.contains(it) } }
                    .forEach { statements.addAll(createIndex(it)) }

                // sync existing columns
                val dataTypeProvider = currentDialect.dataTypeProvider
                val redoColumns = existingTableColumns
                    .mapValues { (col, existingCol) ->
                        val columnType = col.columnType
                        val incorrectNullability = existingCol.nullable != columnType.nullable
                        // Exposed doesn't support changing sequences on columns
                        val incorrectAutoInc = existingCol.autoIncrement != columnType.isAutoInc && col.autoIncColumnType?.autoincSeq == null
                        val incorrectDefaults =
                            existingCol.defaultDbValue != col.dbDefaultValue?.let { dataTypeProvider.dbDefaultToString(col, it) }
                        val incorrectCaseSensitiveName = existingCol.name.inProperCase() != col.nameInDatabaseCase()
                        ColumnDiff(incorrectNullability, incorrectAutoInc, incorrectDefaults, incorrectCaseSensitiveName)
                    }
                    .filterValues { it.hasDifferences() }

                redoColumns.flatMapTo(statements) { (col, changedState) -> col.modifyStatements(changedState) }
            }
        }

        if (dbSupportsAlterTableWithAddColumn) {
            val existingColumnConstraint = logTimeSpent("Extracting column constraints", withLogs) {
                currentDialect.columnConstraints(*tables)
            }

            val foreignKeyConstraints = tables.flatMap { table ->
                table.foreignKeys.map { it to existingColumnConstraint[table to it.from]?.firstOrNull() }
            }

            for ((foreignKey, existingConstraint) in foreignKeyConstraints) {
                if (existingConstraint == null) {
                    statements.addAll(createFKey(foreignKey))
                } else if (existingConstraint.targetTable != foreignKey.targetTable ||
                    foreignKey.deleteRule != existingConstraint.deleteRule ||
                    foreignKey.updateRule != existingConstraint.updateRule
                ) {
                    statements.addAll(existingConstraint.dropStatement())
                    statements.addAll(createFKey(foreignKey))
                }
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
            } else throw exception
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
            } else throw exception
        }
    }

    /**
     * This function should be used in cases when you want an easy-to-use auto-actualization of database scheme.
     * It will create all absent tables, add missing columns for existing tables if it's possible (columns are nullable or have default values).
     *
     * Also if there is inconsistency in DB vs code mappings (excessive or absent indexes)
     * then DDLs to fix it will be logged to exposedLogger.
     *
     * This functionality is based on jdbc metadata what might be a bit slow, so it is recommended to call this function once
     * at application startup and provide all tables you want to actualize.
     *
     * Please note, that execution of this function concurrently might lead to unpredictable state in database due to
     * non-transactional behavior of some DBMS on processing DDL statements (e.g. MySQL) and metadata caches.

     * To prevent such cases is advised to use any "global" synchronization you prefer (via redis, memcached, etc) or
     * with Exposed's provided lock based on synchronization on a dummy "Buzy" table (@see SchemaUtils#withDataBaseLock).
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
                val modifyTablesStatements = checkMappingConsistence(tables = tables, withLogs).filter { it !in executedStatements }
                execStatements(inBatch, modifyTablesStatements)
                commit()
            }
            db.dialect.resetCaches()
        }
    }

    /**
     * The function provides a list of statements those need to be executed to make
     * existing table definition compatible with Exposed tables mapping.
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
            checkMappingConsistence(tables = tablesToAlter.toTypedArray(), withLogs).filter { it !in executedStatements }
        }
        return executedStatements + modifyTablesStatements
    }

    /**
     * Log Exposed table mappings <-> real database mapping problems and returns DDL Statements to fix them
     */
    fun checkMappingConsistence(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (withLogs) {
            checkExcessiveIndices(tables = tables)
        }
        return checkMissingIndices(tables = tables, withLogs).flatMap { it.createStatement() }
    }

    fun checkExcessiveIndices(vararg tables: Table) {
        val excessiveConstraints = currentDialect.columnConstraints(*tables).filter { it.value.size > 1 }

        if (excessiveConstraints.isNotEmpty()) {
            exposedLogger.warn("List of excessive foreign key constraints:")
            excessiveConstraints.forEach { (pair, fk) ->
                val constraint = fk.first()
                val fkPartToLog = fk.joinToString(", ") { it.fkName }
                exposedLogger.warn(
                    "\t\t\t'${pair.first}'.'${pair.second}' -> '${constraint.fromTableName}':\t$fkPartToLog"
                )
            }

            exposedLogger.info("SQL Queries to remove excessive keys:")
            excessiveConstraints.forEach { (_, value) ->
                value.take(value.size - 1).forEach {
                    exposedLogger.info("\t\t\t${it.dropStatement()};")
                }
            }
        }

        val excessiveIndices =
            currentDialect.existingIndices(*tables).flatMap { it.value }.groupBy { Triple(it.table, it.unique, it.columns.joinToString { it.name }) }
                .filter { it.value.size > 1 }
        if (excessiveIndices.isNotEmpty()) {
            exposedLogger.warn("List of excessive indices:")
            excessiveIndices.forEach { (triple, indices) ->
                exposedLogger.warn("\t\t\t'${triple.first.tableName}'.'${triple.third}' -> ${indices.joinToString(", ") { it.indexName }}")
            }
            exposedLogger.info("SQL Queries to remove excessive indices:")
            excessiveIndices.forEach {
                it.value.take(it.value.size - 1).forEach {
                    exposedLogger.info("\t\t\t${it.dropStatement()};")
                }
            }
        }
    }

    /** Returns list of indices missed in database **/
    private fun checkMissingIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        fun Collection<Index>.log(mainMessage: String) {
            if (withLogs && isNotEmpty()) {
                exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
            }
        }

        val isMysql = currentDialect is MysqlDialect
        val isSQLite = currentDialect is SQLiteDialect
        val fKeyConstraints = currentDialect.columnConstraints(*tables).keys
        val existingIndices = currentDialect.existingIndices(*tables)
        fun List<Index>.filterFKeys() = if (isMysql) {
            filterNot { it.table to LinkedHashSet(it.columns) in fKeyConstraints }
        } else {
            this
        }

        // SQLite: indices whose names start with "sqlite_" are meant for internal use
        fun List<Index>.filterInternalIndices() = if (isSQLite) {
            filter { !it.indexName.startsWith("sqlite_") }
        } else {
            this
        }

        val missingIndices = HashSet<Index>()
        val notMappedIndices = HashMap<String, MutableSet<Index>>()
        val nameDiffers = HashSet<Index>()

        for (table in tables) {
            val existingTableIndices = existingIndices[table].orEmpty().filterFKeys().filterInternalIndices()
            val mappedIndices = table.indices.filterFKeys().filterInternalIndices()

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

            notMappedIndices.getOrPut(table.nameInDatabaseCase()) { hashSetOf() }.addAll(existingTableIndices.subtract(mappedIndices))

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

    fun drop(vararg tables: Table, inBatch: Boolean = false) {
        if (tables.isEmpty()) return
        with(TransactionManager.current()) {
            var tablesForDeletion =
                sortTablesByReferences(tables.toList())
                    .reversed()
                    .filter { it in tables }
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
     * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests
     *
     * @param schemas the names of the schema
     * @param cascade flag to drop schema and all of its objects and all objects that depend on those objects.
     * You don't have to specify this option when you are using Mysql or MariaDB
     * because whether you specify it or not, all objects in the schema will be dropped.
     * @param inBatch flag to perform schema creation in a single batch
     */
    fun dropSchema(vararg schemas: Schema, cascade: Boolean = false, inBatch: Boolean = false) {
        if (schemas.isEmpty()) return
        with(TransactionManager.current()) {
            val schemasForDeletion = if (currentDialect.supportsIfNotExists) schemas.distinct() else schemas.distinct().filter { it.exists() }
            val dropStatements = schemasForDeletion.flatMap { it.dropStatement(cascade) }

            execStatements(inBatch, dropStatements)

            currentDialect.resetSchemaCaches()
        }
    }
}
