package org.jetbrains.exposed.v1.migration

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import java.io.File

/**
 * Utility functions that assist with generating the necessary SQL statements to migrate database schema objects.
 */
object MigrationUtils : MigrationUtilityApi() {
    /**
     * This function simply generates the migration script without applying the migration. Its purpose is to show what
     * the migration script will look like before applying the migration. If a migration script with the same name
     * already exists, its content will be overwritten.
     *
     * @param tables The tables whose changes will be used to generate the migration script.
     * @param scriptName The name to be used for the generated migration script.
     * @param scriptDirectory The directory (path from repository root) in which to create the migration script.
     * @param withLogs By default, a description for each intermediate step, as well as its execution time, is logged at
     * the INFO level. This can be disabled by setting [withLogs] to `false`.
     *
     * @return The generated migration script.
     *
     * @throws IllegalArgumentException if no argument is passed for the [tables] parameter.
     */
    @ExperimentalDatabaseMigrationApi
    fun generateMigrationScript(
        vararg tables: Table,
        scriptDirectory: String,
        scriptName: String,
        withLogs: Boolean = true
    ): File {
        require(tables.isNotEmpty()) { "Tables argument must not be empty" }

        val allStatements = statementsRequiredForDatabaseMigration(*tables, withLogs = withLogs)

        @OptIn(InternalApi::class)
        return allStatements.writeMigrationScriptTo("$scriptDirectory/$scriptName.sql")
    }

    /**
     * Returns the SQL statements that need to be executed to make the existing database schema compatible with
     * the table objects defined using Exposed. Unlike `SchemaUtils.statementsRequiredToActualizeScheme()`,
     * DROP/DELETE statements are included.
     *
     * **Note:** Some databases, like **SQLite**, only support `ALTER TABLE ADD COLUMN` syntax in very restricted cases,
     * which may cause unexpected behavior when adding some missing columns. For more information,
     * refer to the relevant documentation.
     * For SQLite, see [ALTER TABLE restrictions](https://www.sqlite.org/lang_altertable.html#alter_table_add_column).
     *
     * **Note:** If this method is called on a **PostgreSQL** database, it will check for a mapping inconsistency
     * between the specified [tables] and existing sequences that have a relational dependency on any of these [tables]
     * (for example, any sequence automatically associated with a `SERIAL` column registered to `IdTable`). This means
     * that an unbound sequence created manually via the `CREATE SEQUENCE` command will no longer be checked and will
     * not generate a DROP statement.
     * When called on other databases, such an inconsistency will be checked against all sequences from the database,
     * potentially returning DROP statements for any sequence unlinked or unrelated to [tables].
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     */
    fun statementsRequiredForDatabaseMigration(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val (tablesToCreate, tablesToAlter) = tables.partition { !it.exists() }

        @OptIn(InternalApi::class)
        val createStatements = logTimeSpent(CREATE_TABLES_LOG_MESSAGE, withLogs) {
            createTableStatements(tables = tablesToCreate.toTypedArray())
        }

        @OptIn(InternalApi::class)
        val createSequencesStatements = logTimeSpent(CREATE_SEQUENCES_LOG_MESSAGE, withLogs) {
            checkMissingSequences(tables = tables, withLogs).flatMap { it.createStatement() }
        }

        @OptIn(InternalApi::class)
        val alterStatements = logTimeSpent(ALTER_TABLES_LOG_MESSAGE, withLogs) {
            addMissingAndDropUnmappedColumns(tables = tablesToAlter.toTypedArray(), withLogs)
        }

        @OptIn(InternalApi::class)
        val modifyTablesStatements = logTimeSpent(MAPPING_CONSISTENCE_LOG_MESSAGE, withLogs) {
            mappingConsistenceRequiredStatements(
                tables = tablesToAlter.toTypedArray(),
                withLogs
            ).filter { it !in (createStatements + alterStatements) }
        }

        val allStatements = createStatements + createSequencesStatements + alterStatements + modifyTablesStatements
        return allStatements
    }

    private fun createTableStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()
        @OptIn(InternalApi::class)
        val toCreate = tables.toList().sortByReferences().filterNot { it.exists() }
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

    @OptIn(InternalApi::class)
    private fun addMissingAndDropUnmappedColumns(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()
        val statements = ArrayList<String>()

        @OptIn(InternalApi::class)
        val existingTablesColumns = logTimeSpent(COLUMNS_LOG_MESSAGE, withLogs) {
            currentDialectMetadata.tableColumns(*tables)
        }

        @OptIn(InternalApi::class)
        val existingPrimaryKeys = logTimeSpent(PRIMARY_KEYS_LOG_MESSAGE, withLogs) {
            currentDialectMetadata.existingPrimaryKeys(*tables)
        }
        val tr = TransactionManager.current()
        val dbSupportsAlterTableWithAddColumn = tr.db.supportsAlterTableWithAddColumn
        val dbSupportsAlterTableWithDropColumn = tr.db.supportsAlterTableWithDropColumn

        val isIncorrectType = { columnMetadata: ColumnMetadata, column: Column<*> ->
            !TransactionManager.current().db.metadata { areEquivalentColumnTypes(columnMetadata.sqlType, columnMetadata.jdbcType, column.columnType.sqlType()) }
        }

        @OptIn(InternalApi::class)
        for (table in tables) {
            table.mapMissingColumnStatementsTo(
                statements, existingTablesColumns[table].orEmpty(), existingPrimaryKeys[table], dbSupportsAlterTableWithAddColumn, isIncorrectType
            )
        }
        @OptIn(InternalApi::class)
        if (dbSupportsAlterTableWithAddColumn) {
            val existingColumnConstraints = logTimeSpent(CONSTRAINTS_LOG_MESSAGE, withLogs) {
                currentDialectMetadata.columnConstraints(*tables)
            }
            mapMissingConstraintsTo(statements, existingColumnConstraints, tables = tables)
        }
        @OptIn(InternalApi::class)
        if (dbSupportsAlterTableWithDropColumn) {
            for (table in tables) {
                table.mapUnmappedColumnStatementsTo(statements, existingTablesColumns[table].orEmpty())
            }
        }
        return statements
    }

    /**
     * Returns the SQL statements that drop any columns that exist in the database but are not defined in [tables].
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * **Note:** Some databases, like **SQLite**, only support `ALTER TABLE DROP COLUMN` syntax in very restricted cases,
     * which may cause unexpected behavior when dropping some unmapped columns. For more information,
     * refer to the relevant documentation.
     * For SQLite, see [ALTER TABLE restrictions](https://www.sqlite.org/lang_altertable.html#alter_table_drop_column).
     */
    fun dropUnmappedColumnsStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = mutableListOf<String>()

        val dbSupportsAlterTableWithDropColumn = TransactionManager.current().db.supportsAlterTableWithDropColumn

        @OptIn(InternalApi::class)
        if (dbSupportsAlterTableWithDropColumn) {
            val existingTablesColumns = logTimeSpent(COLUMNS_LOG_MESSAGE, withLogs) {
                currentDialectMetadata.tableColumns(*tables)
            }

            tables.forEach { table ->
                table.mapUnmappedColumnStatementsTo(statements, existingTablesColumns[table].orEmpty())
            }
        }

        return statements
    }

    /**
     * Returns the SQL statements that drop any indices that exist in the database but are not defined in [tables].
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * @param tables The table definitions to check for unmapped indices
     * @param withLogs Whether to log intermediate steps and execution time. Defaults to `true`
     * @return A list of SQL DROP INDEX statements for unmapped indices
     */
    @OptIn(InternalApi::class)
    fun dropUnmappedIndices(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val foreignKeyConstraints = currentDialectMetadata.columnConstraints(*tables).keys
        val existingIndices = currentDialectMetadata.existingIndices(*tables)

        val (_, toDrop) = existingIndices.filterAndLogMissingAndUnmappedIndices(foreignKeyConstraints, withDropIndices = true, withLogs = withLogs, tables = tables)

        return toDrop.flatMap {
            it.dropStatement()
        }
    }

    /**
     * Returns the SQL statements that drop any sequences that exist in the database but are not defined in [tables].
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     *
     * @param tables The table definitions to check for unmapped sequences
     * @param withLogs Whether to log intermediate steps and execution time. Defaults to `true`
     * @return A list of SQL DROP SEQUENCE statements for unmapped sequences
     */
    fun dropUnmappedSequences(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val sequences = checkUnmappedSequences(tables = tables, withLogs = withLogs)

        return sequences.flatMap { it.dropStatement() }
    }

    /**
     * Log Exposed table mappings <-> real database mapping problems and returns DDL Statements to fix them, including
     * DROP/DELETE statements (unlike [checkMappingConsistence]).
     *
     * **Note:** If this method is called on a PostgreSQL database, only sequences with a relational dependency on any
     * of the specified [tables] will be checked for a mapping inconsistency. When called on other databases, all sequences
     * from the database will be checked, potentially returning SQL statements to drop any sequences that are unlinked
     * or unrelated to [tables].
     */
    private fun mappingConsistenceRequiredStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val foreignKeyConstraints = currentDialectMetadata.columnConstraints(*tables)
        val existingIndices = currentDialectMetadata.existingIndices(*tables)

        @OptIn(InternalApi::class)
        val filteredIndices = existingIndices.filterAndLogMissingAndUnmappedIndices(
            foreignKeyConstraints.keys, withDropIndices = true, withLogs, tables = tables
        )
        val (createMissing, dropUnmapped) = filteredIndices

        @OptIn(InternalApi::class)
        return createMissing.flatMap { it.createStatement() } +
            dropUnmapped.flatMap { it.dropStatement() } +
            foreignKeyConstraints.filterAndLogExcessConstraints(withLogs).flatMap { it.dropStatement() } +
            existingIndices.filterAndLogExcessIndices(withLogs).flatMap { it.dropStatement() } +
            checkUnmappedSequences(tables = tables, withLogs).flatMap { it.dropStatement() } +
            checkMissingCheckConstraints(tables = tables, withLogs).flatMap { it.createStatement() } +
            checkUnmappedCheckConstraints(tables = tables, withLogs).flatMap { it.dropStatement() }
    }

    /**
     * Checks all [tables] for any that have sequences that are missing in the database but are defined in the code. If
     * found, this function also logs the SQL statements that can be used to create these sequences.
     *
     * @return List of sequences that are missing and can be created.
     */
    private fun checkMissingSequences(vararg tables: Table, withLogs: Boolean): List<Sequence> {
        if (!currentDialect.supportsCreateSequence) return emptyList()

        // all possible sequences checked, as 'mappedSequences' is the limiting factor, not 'existingSequencesNames'
        val existingSequencesNames: Set<String> = currentDialectMetadata.sequences().toSet()

        @OptIn(InternalApi::class)
        return existingSequencesNames.filterMissingSequences(tables = tables).also {
            it.log("Sequences missed from database (will be created):", withLogs)
        }
    }

    /**
     * Checks all [tables] for any that have sequences that exist in the database but are not mapped in the code. If
     * found, this function also logs the SQL statements that can be used to drop these sequences.
     *
     * **Note:** If this method is called on a PostgreSQL database, only sequences with a relational dependency on any
     * of the specified [tables] will be checked for a mapping in Exposed code. When called on other databases, all sequences
     * from the database will be checked, potentially returning any [Sequence] unlinked or unrelated to [tables].
     *
     * @return List of sequences that are unmapped and can be dropped.
     */
    private fun checkUnmappedSequences(vararg tables: Table, withLogs: Boolean): List<Sequence> {
        if (!currentDialect.supportsCreateSequence) {
            return emptyList()
        }

        fun Collection<Sequence>.log(mainMessage: String) {
            if (withLogs && isNotEmpty()) {
                exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
            }
        }

        val existingSequencesNames: Set<String> = if (currentDialect is PostgreSQLDialect) {
            // only sequences with related links to [tables] are checked, to avoid dropping every unmapped sequence
            currentDialectMetadata.existingSequences(*tables).values.flatMap { it.map { it.name } }
        } else {
            currentDialectMetadata.sequences()
        }.toSet()

        @OptIn(InternalApi::class)
        return existingSequencesNames.filterUnmappedSequences(tables = tables).also {
            it.log("Sequences exist in database and not mapped in code:", withLogs)
        }
    }

    /**
     * Checks all [tables] for any that have CHECK constraints that are missing in the database but are defined in the code.
     * If found, this function also logs the CHECK constraints that will be created.
     *
     * @return List of CHECK constraints that are missing and can be created.
     */
    private fun checkMissingCheckConstraints(vararg tables: Table, withLogs: Boolean): List<CheckConstraint> {
        fun Collection<CheckConstraint>.log(mainMessage: String) {
            if (withLogs && isNotEmpty()) {
                exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
            }
        }

        if (!currentDialect.supportsColumnTypeChange) {
            return emptyList()
        }

        val missingCheckConstraints = mutableListOf<CheckConstraint>()
        tables.forEach { table ->
            val mappedCheckConstraints = table.checkConstraints()
            val existingCheckConstraints = currentDialectMetadata.existingCheckConstraints(*tables)[table].orEmpty()
            val existingCheckConstraintsNames = existingCheckConstraints.map { it.checkName.uppercase() }.toSet()
            missingCheckConstraints.addAll(mappedCheckConstraints.filterNot { it.checkName.uppercase() in existingCheckConstraintsNames })
        }
        missingCheckConstraints.log("CHECK constraints missed from database (will be created):")
        return missingCheckConstraints
    }

    /**
     * Checks all [tables] for any that have CHECK constraints that exist in the database but are not mapped in the code.
     * If found, this function also logs the CHECK constraints that will be dropped.
     *
     * @return List of CHECK constraints that are unmapped and can be dropped.
     */
    private fun checkUnmappedCheckConstraints(vararg tables: Table, withLogs: Boolean): List<CheckConstraint> {
        fun Collection<CheckConstraint>.log(mainMessage: String) {
            if (withLogs && isNotEmpty()) {
                exposedLogger.warn(joinToString(prefix = "$mainMessage\n\t\t", separator = "\n\t\t"))
            }
        }

        if (!currentDialect.supportsColumnTypeChange) {
            return emptyList()
        }

        val unmappedCheckConstraints = mutableListOf<CheckConstraint>()
        tables.forEach { table ->
            val existingCheckConstraints = currentDialectMetadata.existingCheckConstraints(*tables)[table].orEmpty()
            val mappedCheckConstraints = table.checkConstraints()
            val mappedCheckConstraintsNames = mappedCheckConstraints.map { it.checkName.uppercase() }.toSet()

            unmappedCheckConstraints.addAll(existingCheckConstraints.filterNot { it.checkName.uppercase() in mappedCheckConstraintsNames })
        }
        unmappedCheckConstraints.log("CHECK constraints exist in database and not mapped in code:")
        return unmappedCheckConstraints
    }
}
