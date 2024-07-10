@file:OptIn(ExperimentalDatabaseMigrationApi::class)

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.util.*

const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
const val USER = "root"
const val PASSWORD = ""
const val MIGRATIONS_DIRECTORY = "migrations" // Location of migration scripts

fun main() {
    val database = Database.connect(
        url = URL,
        user = USER,
        driver = "org.h2.Driver",
        password = PASSWORD
    )

    val flyway = Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .locations("filesystem:$MIGRATIONS_DIRECTORY")
        .baselineOnMigrate(true) // Used when migrating an existing database for the first time
        .load()

    transaction(database) {
        simulateExistingDatabase(database)

        println("*** Before migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(Users)[Users]}")

        // This will generate a migration script in the path exposed-migration/migrations
        MigrationUtils.generateMigrationScript(
            Users,
            scriptDirectory = MIGRATIONS_DIRECTORY,
            scriptName = "V2__Add_primary_key",
        )
    }

    transaction(database) {
        // This can be commented out to review the generated migration script before applying a migration
        flyway.migrate()
    }

    transaction(database) {
        println("*** After migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(Users)[Users]}")

        Users.insert {
            it[id] = UUID.randomUUID()
            it[email] = "root2@root.com"
        }
    }
}

fun simulateExistingDatabase(database: Database) {
    transaction(database) {
        exec("CREATE TABLE IF NOT EXISTS USERS (ID UUID NOT NULL, EMAIL VARCHAR(320) NOT NULL)")
        exec("INSERT INTO USERS (EMAIL, ID) VALUES ('root1@root.com', '05fb3246-9387-4d04-a27f-fa0107c33883')")
    }
}
