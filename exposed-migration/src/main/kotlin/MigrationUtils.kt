import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SchemaUtils.statementsRequiredForDatabaseMigration
import org.jetbrains.exposed.sql.Table
import java.io.File

object MigrationUtils {
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
