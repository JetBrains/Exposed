package org.example

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.util.*
import org.example.migrations.*
import org.example.tables.UsersTable
import org.jetbrains.exposed.sql.Database

const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
const val USER = "root"
const val PASSWORD = ""
const val MIGRATIONS_DIRECTORY = "migrations" // Location of migration scripts

fun main() {
    val h2db = Database.connect(
        url =  URL,
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
        println("Primary key: ${currentDialect.existingPrimaryKeys(UsersTable)[UsersTable]}")

        generateMigrationScript()
    }

    transaction(h2db) {
        // This can be commented out to review the generated migration script before applying a migration
        flyway.migrate()
    }

    transaction(h2db) {
        println("*** After migration ***")
        println("Primary key: ${currentDialect.existingPrimaryKeys(UsersTable)[UsersTable]}")

        UsersTable.insert {
            it[id] = UUID.randomUUID()
            it[email] = "root2@root.com"
        }
    }
}
