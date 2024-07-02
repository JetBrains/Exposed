import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.util.*

const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
const val USER = "root"
const val PASSWORD = ""

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val database = Database.connect(
        url = URL,
        user = USER,
        driver = "org.h2.Driver",
        password = PASSWORD
    )

    val flyway = Flyway.configure()
        .dataSource(URL, USER, PASSWORD)
        .locations("filesystem:migrations") // Location of migration scripts
        .baselineOnMigrate(true) // Used when migrating an existing database for the first time
        .load()

    transaction(database) {
        SchemaUtils.create(UsersV1)

        UsersV1.insert {
            it[id] = UUID.randomUUID()
            it[email] = "root1@root.com"
        }

        println("*** Before migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(UsersV1)[UsersV1]}")
    }

    transaction {
        MigrationUtils.generateMigrationScript(
            UsersV2,
            scriptDirectory = "migrations",
            scriptName = "V2__Add_primary_key",
        )

        flyway.migrate()
    }

    transaction {
        println("*** After migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(UsersV1)[UsersV1]}")

        UsersV2.insert {
            it[id] = UUID.randomUUID()
            it[email] = "root2@root.com"
        }
    }
}
