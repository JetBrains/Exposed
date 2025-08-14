@file:OptIn(ExperimentalDatabaseMigrationApi::class)

import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.MigrationUtils

const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
const val USER = "root"
const val PASSWORD = ""
const val MIGRATIONS_DIRECTORY = "migrations" // Location of migration scripts

val database = Database.connect(
    url = URL,
    user = USER,
    driver = "org.h2.Driver",
    password = PASSWORD
)

fun main() {
    simulateExistingDatabase()

    transaction(database) {
        generateMigrationScript()
    }
}

fun simulateExistingDatabase() {
    transaction(database) {
        exec("DROP TABLE IF EXISTS USERS")
        exec("CREATE TABLE IF NOT EXISTS USERS (ID UUID NOT NULL, EMAIL VARCHAR(320) NOT NULL)")
        exec("INSERT INTO USERS (EMAIL, ID) VALUES ('root1@root.com', '05fb3246-9387-4d04-a27f-fa0107c33883')")
    }
}

fun generateMigrationScript() {
    // This will generate a migration script in the path exposed-migration/migrations
    MigrationUtils.generateMigrationScript(
        Users,
        scriptDirectory = MIGRATIONS_DIRECTORY,
        scriptName = "V2__Add_primary_key",
    )
}
