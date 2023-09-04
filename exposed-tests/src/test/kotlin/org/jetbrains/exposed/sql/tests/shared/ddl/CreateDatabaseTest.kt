package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertFailsWith

class CreateDatabaseTest : DatabaseTestsBase() {

    @Test
    fun testCreateAndDropDatabase() {
        withDb(excludeSettings = listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.ORACLE)) {
            val dbName = "jetbrains"
            try {
                SchemaUtils.dropDatabase(dbName)
            } catch (cause: SQLException) {
                // ignore
            }
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
        }
    }

    @Test
    fun testListDatabasesOracle() {
        withDb(TestDB.ORACLE) {
            assertFailsWith<IllegalStateException> {
                SchemaUtils.listDatabases()
            }
        }
    }

    @Test
    fun testListDatabasesPostgres() {
        withDb(TestDB.POSTGRESQL) {
            connection.autoCommit = true
            val dbName = "jetbrains"
            val initial = SchemaUtils.listDatabases()
            if (dbName in initial) {
                SchemaUtils.dropDatabase(dbName)
            }

            SchemaUtils.createDatabase(dbName)
            val created = SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            SchemaUtils.dropDatabase(dbName)
            val deleted = SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
            connection.autoCommit = false
        }
    }

    @Test
    fun testListDatabases() {
        withDb(excludeSettings = listOf(TestDB.ORACLE, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)) {
            val dbName = "jetbrains"
            val initial = SchemaUtils.listDatabases()
            if (dbName in initial) {
                SchemaUtils.dropDatabase(dbName)
            }

            SchemaUtils.createDatabase(dbName)
            val created = SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            SchemaUtils.dropDatabase(dbName)
            val deleted = SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
        }
    }

    @Test
    fun testCreateAndDropDatabaseInPostgresql() {
        // PostgreSQL needs auto commit to be "ON" to allow create database statement
        withDb(listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)) {
            connection.autoCommit = true
            val dbName = "jetbrains"
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
            connection.autoCommit = false
        }
    }
}
