import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.insert
import org.jetbrains.exposed.v1.transactions.transaction
import java.util.*

fun main() {
    val flyway = Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .locations("filesystem:$MIGRATIONS_DIRECTORY")
        .baselineOnMigrate(true) // Used when migrating an existing database for the first time
        .load()

    simulateExistingDatabase()

    transaction(database) {
        println("*** Before migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(Users)[Users]}")

        generateMigrationScript()
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
