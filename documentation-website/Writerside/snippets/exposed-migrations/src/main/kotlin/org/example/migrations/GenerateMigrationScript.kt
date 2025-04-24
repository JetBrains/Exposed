@file:OptIn(ExperimentalDatabaseMigrationApi::class)

package org.example.migrations

import MigrationUtils
import org.example.tables.UsersTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.transactions.transaction

private const val MIGRATIONS_DIRECTORY = "migrations" // Location of migration scripts

val h2db = Database.connect(
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    driver = "org.h2.Driver",
    user = "root",
    password = ""
)

//val sqliteDb = Database.connect(
//    "jdbc:sqlite:file:test?mode=memory&cache=shared",
//    "org.sqlite.JDBC",
//    databaseConfig = DatabaseConfig { useNestedTransactions = true }
//)
//
//val mysqlDb = Database.connect(
//    "jdbc:mysql://localhost:3306/test",
//    driver = "com.mysql.cj.jdbc.Driver",
//    user = "root",
//    password = "password",
//)

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
    // This will generate a migration script in the path exposed-migration/migrations
    MigrationUtils.generateMigrationScript(
        UsersTable,
        scriptDirectory = MIGRATIONS_DIRECTORY,
        scriptName = "V2__Add_primary_key",
    )
}
