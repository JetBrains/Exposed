package org.example

import org.example.tables.UsersTable
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import kotlin.uuid.Uuid

/*
    Important: The contents of this file are referenced by line number in `Migrations.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
const val USER = "root"
const val PASSWORD = ""
const val MIGRATIONS_DIRECTORY = "src/main/kotlin/org/example/migrations" // Location of migration scripts

fun main() {
    val h2db = Database.connect(
        url = URL,
        driver = "org.h2.Driver",
        user = USER,
        password = PASSWORD
    )

    val flyway = Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .locations("filesystem:$MIGRATIONS_DIRECTORY")
        .baselineOnMigrate(true) // Used when migrating an existing database for the first time
        .load()

    simulateExistingDatabase(h2db)

    transaction(h2db) {
        println("*** Before migration ***")
        println("Primary key: ${currentDialectMetadata.existingPrimaryKeys(UsersTable)[UsersTable]}")

        // Generate a migration script
        generateMigrationScript()
    }

    transaction(h2db) {
        // Generate SQL statements required to align the database schema
        // against the current table definitions
        val statements = MigrationUtils.statementsRequiredForDatabaseMigration(
            UsersTable
        )
        println(statements)

        // Disable logging
        MigrationUtils.statementsRequiredForDatabaseMigration(
            UsersTable,
            withLogs = false
        )

        // Identify columns that are no longer present in the current table definitions
        // and return the SQL statements to remove them
        val dropStatements = MigrationUtils.dropUnmappedColumnsStatements(
            UsersTable
        )
        println(dropStatements)

        // SchemaUtils methods
        val missingColStatements = SchemaUtils.addMissingColumnsStatements(
            UsersTable
        )
        println(missingColStatements)

        // This can be commented out to review the generated migration script before applying a migration
        flyway.migrate()
    }

    transaction(h2db) {
        println("*** After migration ***")
        println("Primary key: ${currentDialectMetadata.existingPrimaryKeys(UsersTable)[UsersTable]}")

        UsersTable.insert {
            it[id] = Uuid.random()
            it[email] = "root2@root.com"
        }
    }
}
