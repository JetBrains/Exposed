package org.jetbrains.exposed.sql

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.r2dbc.sql.deleteAll
import org.jetbrains.exposed.r2dbc.sql.exists
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectMetadata

/** Utility functions that assist with creating, altering, and dropping database schema objects. */
@Suppress("TooManyFunctions", "LargeClass")
object SchemaUtils : SchemaUtilityApi() {
    /** Returns a list of [tables] sorted according to the targets of their foreign key constraints, if any exist. */
    fun sortTablesByReferences(tables: Iterable<Table>): List<Table> {
        @OptIn(InternalApi::class)
        return tables.sortByReferences()
    }

    /** Checks whether any of the [tables] have a sequence of foreign key constraints that cycle back to them. */
    fun checkCycle(vararg tables: Table): Boolean {
        @OptIn(InternalApi::class)
        return tables.toList().hasCycle()
    }

    /** Returns the SQL statements that create all [tables] that do not already exist. */
    suspend fun createStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()

        val toCreate = sortTablesByReferences(tables.toList()).filterNot { it.exists() }
        val alters = arrayListOf<String>()

        @OptIn(InternalApi::class)
        return toCreate.flatMap { table ->
            val existingAutoIncSeq = table.autoIncColumn?.autoIncColumnType?.sequence?.takeIf {
                currentDialectMetadata.sequenceExists(it)
            }
            val (create, alter) = tableDdlWithoutExistingSequence(table, existingAutoIncSeq)
            alters += alter
            create
        } + alters
    }

    /** Creates the provided sequences, using a batch execution if [inBatch] is set to `true`. */
    suspend fun createSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val createStatements = seq.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
        }
    }

    /** Drops the provided sequences, using a batch execution if [inBatch] is set to `true`. */
    suspend fun dropSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val dropStatements = seq.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
        }
    }

    /** Returns the SQL statements that create the provided [ForeignKeyConstraint]. */
    fun createFKey(foreignKey: ForeignKeyConstraint): List<String> {
        @OptIn(InternalApi::class)
        return foreignKey.createDdl()
    }

    /** Returns the SQL statements that create the provided [index]. */
    fun createIndex(index: Index): List<String> = index.createStatement()

    /**
     * Returns the SQL statements that create any columns defined in [tables], which are missing from the existing
     * tables in the database.
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * **Note:** Some databases, like **SQLite**, only support `ALTER TABLE ADD COLUMN` syntax in very restricted cases,
     * which may cause unexpected behavior when adding some missing columns. For more information,
     * refer to the relevant documentation.
     * For SQLite, see [ALTER TABLE restrictions](https://www.sqlite.org/lang_altertable.html#alter_table_add_column).
     */
    suspend fun addMissingColumnsStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = ArrayList<String>()

        @OptIn(InternalApi::class)
        val existingTablesColumns = logTimeSpent(columnsLogMessage, withLogs) {
            currentDialectMetadata.tableColumns(*tables)
        }

        @OptIn(InternalApi::class)
        val existingPrimaryKeys = logTimeSpent(primaryKeysLogMessage, withLogs) {
            currentDialectMetadata.existingPrimaryKeys(*tables)
        }

        val dbSupportsAlterTableWithAddColumn = TransactionManager.current().db.supportsAlterTableWithAddColumn

        @OptIn(InternalApi::class)
        for (table in tables) {
            table.mapMissingColumnStatementsTo(
                statements,
                existingTablesColumns[table].orEmpty(),
                existingPrimaryKeys[table],
                dbSupportsAlterTableWithAddColumn
            )
        }

        @OptIn(InternalApi::class)
        if (dbSupportsAlterTableWithAddColumn) {
            val existingColumnConstraints = logTimeSpent(constraintsLogMessage, withLogs) {
                currentDialectMetadata.columnConstraints(*tables)
            }
            mapMissingConstraintsTo(statements, existingColumnConstraints, tables = tables)
        }

        return statements
    }

    private suspend fun R2dbcTransaction.execStatements(inBatch: Boolean, statements: List<String>) {
        if (inBatch) {
            execInBatch(statements)
        } else {
            for (statement in statements) {
                exec(statement)
            }
        }
    }

    /** Creates all [tables] that do not already exist, using a batch execution if [inBatch] is set to `true`. */
    suspend fun <T : Table> create(vararg tables: T, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            execStatements(inBatch, createStatements(*tables))
            commit()
            currentDialectMetadata.resetCaches()
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
    suspend fun createDatabase(vararg databases: String, inBatch: Boolean = false) {
        val transaction = TransactionManager.current()
        try {
            with(transaction) {
                val createStatements = databases.flatMap { listOf(currentDialect.createDatabase(it)) }
                execStatements(inBatch, createStatements)
            }
        } catch (exception: ExposedSQLException) {
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.getAutoCommit()) {
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
    suspend fun listDatabases(): List<String> {
        val transaction = TransactionManager.current()
        return with(transaction) {
            exec(currentDialect.listDatabases()) { row ->
                row.get(0, String::class.java)?.lowercase()
            }?.filterNotNull()?.toList() ?: emptyList()
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
    suspend fun dropDatabase(vararg databases: String, inBatch: Boolean = false) {
        val transaction = TransactionManager.current()
        try {
            with(transaction) {
                val createStatements = databases.flatMap { listOf(currentDialect.dropDatabase(it)) }
                execStatements(inBatch, createStatements)
            }
        } catch (exception: ExposedSQLException) {
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.getAutoCommit()) {
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
     * **Note:** Some databases, like **SQLite**, only support `ALTER TABLE ADD COLUMN` syntax in very restricted cases,
     * which may cause unexpected behavior when adding some missing columns. For more information,
     * refer to the relevant documentation.
     * For SQLite, see [ALTER TABLE restrictions](https://www.sqlite.org/lang_altertable.html#alter_table_add_column).
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
    @Deprecated(
        "Execution of this function might lead to unpredictable state in the database if a failure occurs at any point. " +
            "To prevent this, please use `MigrationUtils.statementsRequiredForDatabaseMigration` with a third-party migration tool (e.g., Flyway).",
        ReplaceWith("MigrationUtils.statementsRequiredForDatabaseMigration"),
        DeprecationLevel.WARNING
    )
    suspend fun createMissingTablesAndColumns(vararg tables: Table, inBatch: Boolean = false, withLogs: Boolean = true) {
        with(TransactionManager.current()) {
            db.dialectMetadata.resetCaches()
            @OptIn(InternalApi::class)
            val createStatements = logTimeSpent(createTablesLogMessage, withLogs) {
                createStatements(*tables)
            }

            @OptIn(InternalApi::class)
            logTimeSpent(executeCreateTablesLogMessage, withLogs) {
                execStatements(inBatch, createStatements)
                commit()
            }

            @OptIn(InternalApi::class)
            val alterStatements = logTimeSpent(alterTablesLogMessage, withLogs) {
                addMissingColumnsStatements(tables = tables, withLogs)
            }

            @OptIn(InternalApi::class)
            logTimeSpent(executeAlterTablesLogMessage, withLogs) {
                execStatements(inBatch, alterStatements)
                commit()
            }
            val executedStatements = createStatements + alterStatements

            @OptIn(InternalApi::class)
            logTimeSpent(mappingConsistenceLogMessage, withLogs) {
                val modifyTablesStatements = checkMappingConsistence(
                    tables = tables,
                    withLogs
                ).filter { it !in executedStatements }
                execStatements(inBatch, modifyTablesStatements)
                commit()
            }
            db.dialectMetadata.resetCaches()
        }
    }

    /**
     * Returns the SQL statements that need to be executed to make the existing database schema compatible with
     * the table objects defined using Exposed.
     *
     * **Note:** Some databases, like **SQLite**, only support `ALTER TABLE ADD COLUMN` syntax in very restricted cases,
     * which may cause unexpected behavior when adding some missing columns. For more information,
     * refer to the relevant documentation.
     * For SQLite, see [ALTER TABLE restrictions](https://www.sqlite.org/lang_altertable.html#alter_table_add_column).
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     */
    @Deprecated(
        "This function will be removed in future releases.",
        ReplaceWith("MigrationUtils.statementsRequiredForDatabaseMigration"),
        DeprecationLevel.WARNING
    )
    suspend fun statementsRequiredToActualizeScheme(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val (tablesToCreate, tablesToAlter) = tables.partition { !it.exists() }

        @OptIn(InternalApi::class)
        val createStatements = logTimeSpent(createTablesLogMessage, withLogs) {
            createStatements(tables = tablesToCreate.toTypedArray())
        }

        @OptIn(InternalApi::class)
        val alterStatements = logTimeSpent(alterTablesLogMessage, withLogs) {
            addMissingColumnsStatements(tables = tablesToAlter.toTypedArray(), withLogs)
        }
        val executedStatements = createStatements + alterStatements

        @OptIn(InternalApi::class)
        val modifyTablesStatements = logTimeSpent(mappingConsistenceLogMessage, withLogs) {
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
    suspend fun checkMappingConsistence(vararg tables: Table, withLogs: Boolean = true): List<String> {
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
    suspend fun checkExcessiveIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        val existingIndices = currentDialectMetadata.existingIndices(*tables)

        @OptIn(InternalApi::class)
        return existingIndices.filterAndLogExcessIndices(withLogs)
    }

    /**
     * Checks all [tables] for any that have more than one defined foreign key constraint and logs the findings. If
     * found, this function also logs the SQL statements that can be used to drop these foreign key constraints.
     *
     * @return List of foreign key constraints that are excessive and can be dropped.
     */
    suspend fun checkExcessiveForeignKeyConstraints(vararg tables: Table, withLogs: Boolean): List<ForeignKeyConstraint> {
        val excessiveConstraints = currentDialectMetadata.columnConstraints(*tables)

        @OptIn(InternalApi::class)
        return excessiveConstraints.filterAndLogExcessConstraints(withLogs)
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
    private suspend fun checkMissingAndUnmappedIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        val foreignKeyConstraints = currentDialectMetadata.columnConstraints(*tables).keys
        val existingIndices = currentDialectMetadata.existingIndices(*tables)

        @OptIn(InternalApi::class)
        return existingIndices.filterAndLogMissingAndUnmappedIndices(
            foreignKeyConstraints, withDropIndices = false, withLogs, tables = tables
        ).first
    }

    /**
     * Creates table with name "busy" (if not present) and single column to be used as "synchronization" point. Table wont be dropped after execution.
     *
     * All code provided in _body_ closure will be executed only if there is no another code which running under "withDataBaseLock" at same time.
     * That means that concurrent execution of long-running tasks under "database lock" might lead to that only first of them will be really executed.
     */
    suspend fun <T> R2dbcTransaction.withDataBaseLock(body: () -> T) {
        val buzyTable = object : Table("busy") {
            val busy = bool("busy").uniqueIndex()
        }
        create(buzyTable)
        val isBusy = buzyTable.selectAll().forUpdate().firstOrNull() != null
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
     * Retrieves a list of all table names in the current database schema.
     * The names will be returned with schema prefixes if the database supports it.
     *
     * @return A list of table names as strings.
     */
    suspend fun listTables(): List<String> = currentDialectMetadata.allTablesNames()

    /**
     * Returns a list with the names of all the tables in all database schemas.
     * The names will be returned with schema prefixes, if the database supports it, and non-user defined tables,
     * like system information table names, will be included.
     *
     * @return A list of table names as strings.
     */
    suspend fun listTablesInAllSchemas(): List<String> = currentDialectMetadata.allTablesNamesInAllSchemas()

    /** Drops all [tables], using a batch execution if [inBatch] is set to `true`. */
    suspend fun drop(vararg tables: Table, inBatch: Boolean = false) {
        if (tables.isEmpty()) return
        with(TransactionManager.current()) {
            var tablesForDeletion = sortTablesByReferences(tables.toList()).reversed().filter { it in tables }
            if (!currentDialect.supportsIfNotExists) {
                tablesForDeletion = tablesForDeletion.filter { it.exists() }
            }
            val dropStatements = tablesForDeletion.flatMap { it.dropStatement() }
            execStatements(inBatch, dropStatements)
            currentDialectMetadata.resetCaches()
        }
    }

    /**
     * Sets the current default schema to [schema]. Supported by H2, MariaDB, Mysql, Oracle, PostgreSQL and SQL Server.
     * SQLite doesn't support schemas.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests
     */
    suspend fun setSchema(schema: Schema, inBatch: Boolean = false) {
        with(TransactionManager.current()) {
            val setStatements = schema.setSchemaStatement()

            execStatements(inBatch, setStatements)

            currentDialectMetadata.resetCaches()
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
    suspend fun createSchema(vararg schemas: Schema, inBatch: Boolean = false) {
        if (schemas.isEmpty()) return
        with(TransactionManager.current()) {
            val toCreate = schemas.distinct().filterNot { it.exists() }
            val createStatements = toCreate.flatMap { it.createStatement() }
            execStatements(inBatch, createStatements)
            commit()
            currentDialectMetadata.resetSchemaCaches()
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
    suspend fun dropSchema(vararg schemas: Schema, cascade: Boolean = false, inBatch: Boolean = false) {
        if (schemas.isEmpty()) return
        with(TransactionManager.current()) {
            val schemasForDeletion = if (currentDialect.supportsIfNotExists) {
                schemas.distinct()
            } else {
                schemas.distinct().filter { it.exists() }
            }
            val dropStatements = schemasForDeletion.flatMap { it.dropStatement(cascade) }

            execStatements(inBatch, dropStatements)

            currentDialectMetadata.resetSchemaCaches()
        }
    }
}
