@file:OptIn(ExperimentalDatabaseMigrationApi::class)

import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.util.UUID

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
        // DROP TABLE IF EXISTS USERS
        Users.dropStatement().forEach(::exec)

        // simulate an existing table with no primary key
        exec("CREATE TABLE IF NOT EXISTS USERS (ID UUID NOT NULL, EMAIL VARCHAR(320) NOT NULL)")

        // simulate an existing record in the table
        // INSERT INTO USERS (ID, EMAIL) VALUES ('05fb3246-9387-4d04-a27f-fa0107c33883', 'root1@root.com')
        buildStatement {
            Users.insert {
                it[id] = UUID.fromString("05fb3246-9387-4d04-a27f-fa0107c33883")
                it[email] = "root1@root.com"
            }
        }
            .prepareSQL(this, prepared = false)
            .also(::exec)
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
