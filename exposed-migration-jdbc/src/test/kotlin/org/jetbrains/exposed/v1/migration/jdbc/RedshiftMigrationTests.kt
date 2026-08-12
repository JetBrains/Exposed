package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.RedshiftDialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertTrue

@OptIn(ExperimentalDatabaseMigrationApi::class)
class RedshiftMigrationTests {
    @TempDir
    lateinit var scriptDirectory: Path

    @BeforeEach
    fun requireH2() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    @Test
    fun testUnsupportedIndexDoesNotBreakMigrationScriptGeneration() {
        val table = object : Table("redshift_migration_index") {
            val value = integer("value").index()
        }

        withRedshiftDialect {
            try {
                SchemaUtils.create(table)

                val statements = MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
                val script = MigrationUtils.generateMigrationScript(
                    table,
                    scriptDirectory = scriptDirectory.toString(),
                    scriptName = "V1__Redshift_index",
                    withLogs = false
                )

                assertTrue(statements.isEmpty())
                assertTrue(script.readText().isEmpty())
            } finally {
                SchemaUtils.drop(table)
            }
        }
    }

    private fun withRedshiftDialect(statement: JdbcTransaction.() -> Unit) {
        val database = Database.connect(
            url = "jdbc:h2:mem:redshift-migration-${UUID.randomUUID()}",
            driver = TestDB.H2_V2.driver,
            databaseConfig = DatabaseConfig { explicitDialect = RedshiftDialect() }
        )
        try {
            transaction(database) {
                maxAttempts = 1
                statement()
            }
        } finally {
            TransactionManager.closeAndUnregister(database)
        }
    }
}
