import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.statementsRequiredForDatabaseMigration
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.io.File
import javax.sql.DataSource

object MigrationUtils {
    /**
     * Applies a database migration from [oldVersion] to [newVersion]. If a migration script with the same name already
     * exists, the existing one will be used as is and a new one will not be generated. This allows you to generate a
     * migration script before the migration and modify it manually if needed.
     *
     * To generate a migration script without applying a migration, @see [generateMigrationScript].
     *
     * @param tables The tables to which the migration will be applied.
     * @param user The user of the database.
     * @param password The password of the database.
     * @param oldVersion The version to migrate from. Pending migrations up to [oldVersion] are applied before applying the migration to [newVersion].
     * @param newVersion The version to migrate to.
     * @param migrationTitle The title of the migration.
     * @param migrationScriptDirectory The directory in which to create the migration script.
     * @param withLogs By default, a description for each intermediate step, as well as its execution time, is logged at
     * the INFO level. This can be disabled by setting [withLogs] to `false`.
     *
     * @throws ExposedMigrationException if the migration fails.
     */
    @ExperimentalDatabaseMigrationApi
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    fun Database.migrate(
        vararg tables: Table,
        user: String,
        password: String,
        oldVersion: String,
        newVersion: String,
        migrationTitle: String,
        migrationScriptDirectory: String,
        withLogs: Boolean = true
    ) {
        val flyway = Flyway
            .configure()
            .baselineOnMigrate(true)
            .baselineVersion(oldVersion)
            .dataSource(url, user, password)
            .locations("filesystem:$migrationScriptDirectory")
            .load()

        attemptMigration(
            *tables,
            flyway = flyway,
            oldVersion = oldVersion,
            newVersion = newVersion,
            migrationTitle = migrationTitle,
            migrationScriptDirectory = migrationScriptDirectory,
            withLogs = withLogs
        )
    }

    /**
     * Applies a database migration from [oldVersion] to [newVersion]. If a migration script with the same name already
     * exists, the existing one will be used as is and a new one will not be generated. This allows you to generate a
     * migration script before the migration and modify it manually if needed.
     *
     * To generate a migration script without applying a migration, @see [generateMigrationScript].
     *
     * @param tables The tables to which the migration will be applied.
     * @param dataSource The [DataSource] object to be used as a means of getting a connection.
     * @param oldVersion The version to migrate from. Pending migrations up to [oldVersion] are applied before applying the migration to [newVersion].
     * @param newVersion The version to migrate to.
     * @param migrationTitle The title of the migration.
     * @param migrationScriptDirectory The directory in which to create the migration script.
     * @param withLogs By default, a description for each intermediate step, as well as its execution time, is logged at
     * the INFO level. This can be disabled by setting [withLogs] to `false`.
     *
     * @throws ExposedMigrationException if the migration fails.
     */
    @ExperimentalDatabaseMigrationApi
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    fun Database.migrate(
        vararg tables: Table,
        dataSource: DataSource,
        oldVersion: String,
        newVersion: String,
        migrationTitle: String,
        migrationScriptDirectory: String,
        withLogs: Boolean = true
    ) {
        val flyway = Flyway
            .configure()
            .baselineOnMigrate(true)
            .baselineVersion(oldVersion)
            .dataSource(dataSource)
            .locations("filesystem:$migrationScriptDirectory")
            .load()

        attemptMigration(
            *tables,
            flyway = flyway,
            oldVersion = oldVersion,
            newVersion = newVersion,
            migrationTitle = migrationTitle,
            migrationScriptDirectory = migrationScriptDirectory,
            withLogs = withLogs
        )
    }

    @ExperimentalDatabaseMigrationApi
    @Suppress("TooGenericExceptionCaught")
    private fun attemptMigration(
        vararg tables: Table,
        flyway: Flyway,
        oldVersion: String,
        newVersion: String,
        migrationTitle: String,
        migrationScriptDirectory: String,
        withLogs: Boolean
    ) {
        with(TransactionManager.current()) {
            db.dialect.resetCaches()

            try {
                val migrationScript = File("$migrationScriptDirectory/$migrationTitle.sql")
                if (!migrationScript.exists()) {
                    generateMigrationScript(
                        tables = *tables,
                        newVersion = newVersion,
                        title = migrationTitle,
                        scriptDirectory = migrationScriptDirectory,
                        withLogs = withLogs
                    )
                }
            } catch (exception: Exception) {
                throw ExposedMigrationException(
                    exception = exception,
                    message = "Failed to generate migration script for migration from $oldVersion to $newVersion: ${exception.message.orEmpty()}"
                )
            }

            try {
                SchemaUtils.logTimeSpent("Migrating database from $oldVersion to $newVersion", withLogs = true) {
                    val migrateResult: MigrateResult = flyway.migrate()
                    if (withLogs) {
                        exposedLogger.info("Migration of database ${if (migrateResult.success) "succeeded" else "failed"}.")
                    }
                }
            } catch (exception: FlywayException) {
                flyway.repair()
                throw ExposedMigrationException(
                    exception = exception,
                    message = "Migration failed from version $oldVersion to $newVersion: ${exception.message.orEmpty()}"
                )
            }

            db.dialect.resetCaches()
        }
    }

    /**
     * This function simply generates the migration script, using the Flyway naming convention, without applying the
     * migration. Its purpose is to show the user what the migration script will look like before applying the
     * migration. If a migration script with the same name already exists, its content will be overwritten.
     *
     * @param tables The tables whose changes will be used to generate the migration script.
     * @param newVersion The version to migrate to.
     * @param title The title of the migration.
     * @param scriptDirectory The directory in which to create the migration script.
     * @param withLogs By default, a description for each intermediate step, as well as its execution time, is logged at
     * the INFO level. This can be disabled by setting [withLogs] to `false`.
     *
     * @return The generated migration script.
     *
     * @throws IllegalArgumentException if no argument is passed for the [tables] parameter.
     */
    @ExperimentalDatabaseMigrationApi
    fun generateMigrationScript(vararg tables: Table, newVersion: String, title: String, scriptDirectory: String, withLogs: Boolean = true): File {
        return generateMigrationScript(*tables, scriptName = "V${newVersion}__$title", scriptDirectory = scriptDirectory, withLogs = withLogs)
    }

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
    fun generateMigrationScript(vararg tables: Table, scriptDirectory: String, scriptName: String, withLogs: Boolean = true): File {
        require(tables.isNotEmpty()) { "Tables argument must not be empty" }

        val allStatements = statementsRequiredForDatabaseMigration(*tables, withLogs = withLogs)

        val migrationScript = File("$scriptDirectory/$scriptName.sql")
        migrationScript.createNewFile()

        // Clear existing content
        migrationScript.writeText("")

        // Append statements
        allStatements.forEach { statement ->
            // Add semicolon only if it's not already there
            val conditionalSemicolon = if (statement.last() == ';') "" else ";"

            migrationScript.appendText("$statement$conditionalSemicolon\n")
        }

        return migrationScript
    }
}
