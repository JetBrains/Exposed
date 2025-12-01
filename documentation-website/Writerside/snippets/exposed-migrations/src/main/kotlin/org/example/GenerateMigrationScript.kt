@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package org.example

import org.example.tables.UsersTable
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

val h2db = Database.connect(
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    driver = "org.h2.Driver",
    user = "root",
    password = ""
)

fun main() {
    simulateExistingDatabase(h2db)

    transaction(h2db) {
        generateMigrationScript()
    }
}

fun simulateExistingDatabase(database: Database) {
    transaction(database) {
        exec("DROP TABLE IF EXISTS USERS")
        exec("CREATE TABLE IF NOT EXISTS USERS (ID UUID NOT NULL, EMAIL VARCHAR(320) NOT NULL)")
        exec("INSERT INTO USERS (EMAIL, ID) VALUES ('root1@root.com', '05fb3246-9387-4d04-a27f-fa0107c33883')")
    }
}

fun generateMigrationScript() {
    // Generate a migration script in the specified path
    MigrationUtils.generateMigrationScript(
        UsersTable,
        scriptDirectory = MIGRATIONS_DIRECTORY,
        scriptName = "V2__Add_primary_key",
    )
}
