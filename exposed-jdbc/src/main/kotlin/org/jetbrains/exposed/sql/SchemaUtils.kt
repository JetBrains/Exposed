package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.statements.api.SchemaUtilityApi
import org.jetbrains.exposed.sql.transactions.JdbcTransaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect

/** Utility functions that assist with creating, altering, and dropping database schema objects. */
object SchemaUtils : SchemaUtilityApi() {
    /** Returns the SQL statements that create the provided [ForeignKeyConstraint]. */
    fun createFKey(foreignKey: ForeignKeyConstraint): List<String> = foreignKey.createDdl()

    /** Returns the SQL statements that create the provided [index]. */
    fun createIndex(index: Index): List<String> = index.createDdl()

    /** Returns a list of [tables] sorted according to the targets of their foreign key constraints, if any exist. */
    fun sortTablesByReferences(tables: Iterable<Table>): List<Table> = tables.sortByReferences()

    /** Checks whether any of the [tables] have a sequence of foreign key constraints that cycle back to them. */
    fun checkCycle(vararg tables: Table) = tables.toList().hasCycle()

    /** Returns the SQL statements that create all [tables] that do not already exist. */
    fun createStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()

        val toCreate = sortTablesByReferences(tables.toList()).filterNot { it.exists() } // db request
        val alters = arrayListOf<String>()
        return toCreate.flatMap { table ->
            val existingAutoIncSeq = table.autoIncColumn?.autoIncColumnType?.sequence?.takeIf {
                currentDialect.sequenceExists(it) // db request
            }
            val (create, alter) = tableDdlWithoutExistingSequence(table, existingAutoIncSeq)
            alters += alter
            create
        } + alters
    }

    /** Creates the provided sequences, using a batch execution if [inBatch] is set to `true`. */
    fun createSequence(vararg seq: Sequence, inBatch: Boolean = false) {
        // need to potentially adjust this as the TransactionManager interface level
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

        val existingTablesColumns = logTimeSpent(columnsLogMessage, withLogs) {
            currentDialect.tableColumns(*tables) // db request
        }

        val existingPrimaryKeys = logTimeSpent(primaryKeysLogMessage, withLogs) {
            currentDialect.existingPrimaryKeys(*tables) // db request
        }

        val dbSupportsAlterTableWithAddColumn = TransactionManager.current().db.supportsAlterTableWithAddColumn

        for (table in tables) {
            table.mapMissingColumnStatementsTo(
                statements, existingTablesColumns[table].orEmpty(), existingPrimaryKeys[table], dbSupportsAlterTableWithAddColumn
            )
        }

        if (dbSupportsAlterTableWithAddColumn) {
            val existingColumnConstraints = logTimeSpent(constraintsLogMessage, withLogs) {
                currentDialect.columnConstraints(*tables) // db request
            }
            mapMissingConstraintsTo(statements, existingColumnConstraints, tables = tables)
        }

        return statements
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
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.autoCommit) { // db request
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
        val transaction = TransactionManager.current() as JdbcTransaction
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
            if (currentDialect.requiresAutoCommitOnCreateDrop && !transaction.connection.autoCommit) { // db request
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
            val createStatements = logTimeSpent(createTablesLogMessage, withLogs) {
                createStatements(*tables)
            }
            logTimeSpent(executeCreateTablesLogMessage, withLogs) {
                execStatements(inBatch, createStatements)
                commit()
            }

            val alterStatements = logTimeSpent(alterTablesLogMessage, withLogs) {
                addMissingColumnsStatements(tables = tables, withLogs)
            }
            logTimeSpent(executeAlterTablesLogMessage, withLogs) {
                execStatements(inBatch, alterStatements)
                commit()
            }
            val executedStatements = createStatements + alterStatements
            logTimeSpent(mappingConsistenceLogMessage, withLogs) {
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
        val (tablesToCreate, tablesToAlter) = tables.partition { !it.exists() } // db request
        val createStatements = logTimeSpent(createTablesLogMessage, withLogs) {
            createStatements(tables = tablesToCreate.toTypedArray())
        }
        val alterStatements = logTimeSpent(alterTablesLogMessage, withLogs) {
            addMissingColumnsStatements(tables = tablesToAlter.toTypedArray(), withLogs)
        }
        val executedStatements = createStatements + alterStatements
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
    fun checkExcessiveIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        val existingIndices = currentDialect.existingIndices(*tables) // db request
        return existingIndices.filterAndLogExcessIndices(withLogs)
    }

    /**
     * Checks all [tables] for any that have more than one defined foreign key constraint and logs the findings. If
     * found, this function also logs the SQL statements that can be used to drop these foreign key constraints.
     *
     * @return List of foreign key constraints that are excessive and can be dropped.
     */
    fun checkExcessiveForeignKeyConstraints(vararg tables: Table, withLogs: Boolean): List<ForeignKeyConstraint> {
        val excessiveConstraints = currentDialect.columnConstraints(*tables) // db request
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
    private fun checkMissingAndUnmappedIndices(vararg tables: Table, withLogs: Boolean): List<Index> {
        val foreignKeyConstraints = currentDialect.columnConstraints(*tables).keys // db request
        val existingIndices = currentDialect.existingIndices(*tables) // db request

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
        with(TransactionManager.current() as JdbcTransaction) {
            var tablesForDeletion = sortTablesByReferences(tables.toList()).reversed().filter { it in tables }
            if (!currentDialect.supportsIfNotExists) {
                tablesForDeletion = tablesForDeletion.filter { it.exists() } // db request
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
                    connection.catalog = schema.identifier // db request
                }

                is H2Dialect -> {
                    connection.schema = schema.identifier // db request
                }
            }
            currentDialect.resetCaches()
            connection.metadata { resetCurrentScheme() } // db request
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
            val toCreate = schemas.distinct().filterNot { it.exists() } // db request
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
                schemas.distinct().filter { it.exists() } // db request
            }
            val dropStatements = schemasForDeletion.flatMap { it.dropStatement(cascade) }

            execStatements(inBatch, dropStatements)

            currentDialect.resetSchemaCaches()
        }
    }

    private fun Transaction.execStatements(inBatch: Boolean, statements: List<String>) {
        if (inBatch) {
            (this as JdbcTransaction).execInBatch(statements)
        } else {
            for (statement in statements) {
                (this as JdbcTransaction).exec(statement)
            }
        }
    }
}
