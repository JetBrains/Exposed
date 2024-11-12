import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.Sequence
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.autoIncColumnType
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.statements.api.SchemaUtilityApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.io.File

/**
 * Utility suspend functions that assist with generating the necessary SQL statements to migrate database schema objects.
 */
object R2dbcMigrationUtils : SchemaUtilityApi() {
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
    suspend fun generateMigrationScript(
        vararg tables: Table,
        scriptDirectory: String,
        scriptName: String,
        withLogs: Boolean = true
    ): File {
        require(tables.isNotEmpty()) { "Tables argument must not be empty" }

        val allStatements = statementsRequiredForDatabaseMigration(*tables, withLogs = withLogs)

        return allStatements.writeMigrationScriptTo("$scriptDirectory/$scriptName.sql")
    }

    /**
     * Returns the SQL statements that need to be executed to make the existing database schema compatible with
     * the table objects defined using Exposed. Unlike `SchemaUtils.statementsRequiredToActualizeScheme`,
     * DROP/DELETE statements are included.
     *
     * **Note:** Some dialects, like SQLite, do not support `ALTER TABLE ADD COLUMN` syntax completely,
     * which restricts the behavior when adding some missing columns. Please check the documentation.
     *
     * By default, a description for each intermediate step, as well as its execution time, is logged at the INFO level.
     * This can be disabled by setting [withLogs] to `false`.
     */
    suspend fun statementsRequiredForDatabaseMigration(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val (tablesToCreate, tablesToAlter) = tables.partition { !it.exists() } // db request
        val createStatements = logTimeSpent(createTablesLogMessage, withLogs) {
            createTableStatements(tables = tablesToCreate.toTypedArray())
        }
        val createSequencesStatements = logTimeSpent(createSequencesLogMessage, withLogs) {
            checkMissingSequences(tables = tables, withLogs).flatMap { it.createStatement() }
        }
        val alterStatements = logTimeSpent(alterTablesLogMessage, withLogs) {
            addMissingAndDropUnmappedColumns(tables = tablesToAlter.toTypedArray(), withLogs)
        }

        val modifyTablesStatements = logTimeSpent(mappingConsistenceLogMessage, withLogs) {
            mappingConsistenceRequiredStatements(
                tables = tablesToAlter.toTypedArray(),
                withLogs
            ).filter { it !in (createStatements + alterStatements) }
        }

        val allStatements = createStatements + createSequencesStatements + alterStatements + modifyTablesStatements
        return allStatements
    }

    private suspend fun createTableStatements(vararg tables: Table): List<String> {
        if (tables.isEmpty()) return emptyList()

        val toCreate = tables.toList().sortByReferences().filterNot { it.exists() } // db request
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

    private suspend fun addMissingAndDropUnmappedColumns(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = ArrayList<String>()

        val existingTablesColumns = logTimeSpent(columnsLogMessage, withLogs) {
            currentDialect.tableColumns(*tables) // db request
        }

        val existingPrimaryKeys = logTimeSpent(primaryKeysLogMessage, withLogs) {
            currentDialect.existingPrimaryKeys(*tables) // db request
        }

        val tr = TransactionManager.current()
        val dbSupportsAlterTableWithAddColumn = tr.db.supportsAlterTableWithAddColumn
        val dbSupportsAlterTableWithDropColumn = tr.db.supportsAlterTableWithDropColumn

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
     * **Note:** Some dialects, like SQLite, do not support `ALTER TABLE DROP COLUMN` syntax completely.
     * Please check the documentation.
     */
    suspend fun dropUnmappedColumnsStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        if (tables.isEmpty()) return emptyList()

        val statements = mutableListOf<String>()

        val dbSupportsAlterTableWithDropColumn = TransactionManager.current().db.supportsAlterTableWithDropColumn

        if (dbSupportsAlterTableWithDropColumn) {
            val existingTablesColumns = logTimeSpent("Extracting table columns", withLogs) {
                currentDialect.tableColumns(*tables) // db request
            }

            tables.forEach { table ->
                table.mapUnmappedColumnStatementsTo(statements, existingTablesColumns[table].orEmpty())
            }
        }

        return statements
    }

    /**
     * Log Exposed table mappings <-> real database mapping problems and returns DDL Statements to fix them, including
     * DROP/DELETE statements (unlike `SchemaUtils.checkMappingConsistence`).
     */
    private suspend fun mappingConsistenceRequiredStatements(vararg tables: Table, withLogs: Boolean = true): List<String> {
        val foreignKeyConstraints = currentDialect.columnConstraints(*tables) // db request
        val existingIndices = currentDialect.existingIndices(*tables) // db request

        val (createMissing, dropUnmapped) = existingIndices.filterAndLogMissingAndUnmappedIndices(
            foreignKeyConstraints.keys, withDropIndices = true, withLogs, tables = tables
        )
        return createMissing.flatMap { it.createStatement() } +
            dropUnmapped.flatMap { it.dropStatement() } +
            foreignKeyConstraints.filterAndLogExcessConstraints(withLogs).flatMap { it.dropStatement() } +
            existingIndices.filterAndLogExcessIndices(withLogs).flatMap { it.dropStatement() } +
            checkUnmappedSequences(tables = tables, withLogs).flatMap { it.dropStatement() }
    }

    /**
     * Checks all [tables] for any that have sequences that are missing in the database but are defined in the code. If
     * found, this function also logs the SQL statements that can be used to create these sequences.
     *
     * @return List of sequences that are missing and can be created.
     */
    private suspend fun checkMissingSequences(vararg tables: Table, withLogs: Boolean): List<Sequence> {
        if (!currentDialect.supportsCreateSequence) return emptyList()

        val existingSequencesNames: Set<String> = currentDialect.sequences().toSet() // db request

        return existingSequencesNames.filterMissingSequences(tables = tables).also {
            it.log("Sequences missed from database (will be created):", withLogs)
        }
    }

    /**
     * Checks all [tables] for any that have sequences that exist in the database but are not mapped in the code. If
     * found, this function also logs the SQL statements that can be used to drop these sequences.
     *
     * @return List of sequences that are unmapped and can be dropped.
     */
    private suspend fun checkUnmappedSequences(vararg tables: Table, withLogs: Boolean): List<Sequence> {
        if (!currentDialect.supportsCreateSequence || (currentDialect as? H2Dialect)?.majorVersion == H2Dialect.H2MajorVersion.One) {
            return emptyList()
        }

        val existingSequencesNames: Set<String> = currentDialect.sequences().toSet() // db request

        return existingSequencesNames.filterUnmappedSequences(tables = tables).also {
            it.log("Sequences exist in database and not mapped in code:", withLogs)
        }
    }
}
