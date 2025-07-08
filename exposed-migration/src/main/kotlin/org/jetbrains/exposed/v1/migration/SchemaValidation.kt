package org.jetbrains.exposed.v1.migration

import org.jetbrains.exposed.v1.core.Table

/**
 * Asserts that the database schema is aligned with the Exposed table definitions.
 *
 * This function uses MigrationUtils.statementsRequiredForDatabaseMigration() to
 * determine if there are any differences between the database schema and the code definitions.
 *
 * @param tables The tables to verify
 * @param inBatch If true, performs the verification in batch for improved performance
 * @throws SchemaValidationException if the schema is not correct
 */
fun assertSchemaIsCorrect(vararg tables: Table, inBatch: Boolean = false) {
    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = inBatch)

    if (statements.isNotEmpty()) {
        val errorMessage = buildString {
            appendLine("Schema validation failed. Database schema is not aligned with code definitions.")
            appendLine("Required migration statements:")
            statements.forEachIndexed { index, statement ->
                appendLine("${index + 1}. $statement")
            }
        }
        throw SchemaValidationException(errorMessage, statements)
    }
}

/**
 * Verifies that the database schema is aligned with the Exposed table definitions.
 *
 * Alternative version that returns a result instead of throwing an exception.
 *
 * @param tables The tables to verify
 * @param inBatch If true, performs the verification in batch for improved performance
 * @return SchemaValidationResult with information about the validation result
 */
fun validateSchema(vararg tables: Table, inBatch: Boolean = false): SchemaValidationResult {
    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = inBatch)

    return if (statements.isEmpty()) {
        SchemaValidationResult.Valid
    } else {
        SchemaValidationResult.Invalid(statements)
    }
}

/**
 * Exception thrown when schema validation fails.
 *
 * @param message The error message describing the validation failure
 * @param migrationStatements The SQL statements required to fix the schema
 */
class SchemaValidationException(
    message: String,
    val migrationStatements: List<String>
) : Exception(message)

/**
 * Result of schema validation.
 */
sealed class SchemaValidationResult {
    /**
     * The schema is valid and aligned.
     */
    object Valid : SchemaValidationResult()

    /**
     * The schema is invalid and requires migrations.
     *
     * @param statements The SQL statements required to align the schema
     */
    data class Invalid(val statements: List<String>) : SchemaValidationResult()

    /**
     * Checks if the schema is valid.
     *
     * @return true if the schema is valid, false otherwise
     */
    fun isValid(): Boolean = this is Valid

    /**
     * Gets the migration statements required to fix the schema.
     *
     * @return List of SQL statements, empty if schema is valid
     */
    fun getMigrationStatements(): List<String> = when (this) {
        is Valid -> emptyList()
        is Invalid -> statements
    }
}
