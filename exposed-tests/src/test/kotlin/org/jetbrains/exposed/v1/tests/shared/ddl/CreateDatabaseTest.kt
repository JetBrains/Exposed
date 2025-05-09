package org.jetbrains.exposed.v1.tests.shared.ddl

import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertFailsWith

class CreateDatabaseTest : DatabaseTestsBase() {

    @Test
    @Suppress("SwallowedException")
    fun testCreateAndDropDatabase() {
        withDb(excludeSettings = TestDB.ALL_POSTGRES + TestDB.ORACLE) {
            val dbName = "jetbrains"
            try {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            } catch (cause: SQLException) {
                // ignore
            }
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createDatabase(dbName)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
        }
    }

    @Test
    fun testListDatabasesOracle() {
        withDb(TestDB.ORACLE) {
            assertFailsWith<IllegalStateException> {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            }
        }
    }

    @Test
    fun testListDatabasesWithAutoCommit() {
        withDb(TestDB.ALL_POSTGRES + TestDB.SQLSERVER) {
            connection.autoCommit = true
            val dbName = "jetbrains"
            val initial = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            if (dbName in initial) {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            }

            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createDatabase(dbName)
            val created = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            val deleted = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
            connection.autoCommit = false
        }
    }

    @Test
    fun testListDatabases() {
        withDb(excludeSettings = TestDB.ALL_POSTGRES + TestDB.SQLSERVER + TestDB.ORACLE) {
            val dbName = "jetbrains"
            val initial = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            if (dbName in initial) {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            }

            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createDatabase(dbName)
            val created = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            val deleted = org.jetbrains.exposed.v1.jdbc.SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
        }
    }

    @Test
    fun testCreateAndDropDatabaseWithAutoCommit() {
        // PostgreSQL needs auto commit to be "ON" to allow create database statement
        withDb(TestDB.ALL_POSTGRES) {
            connection.autoCommit = true
            val dbName = "jetbrains"
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.createDatabase(dbName)
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.dropDatabase(dbName)
            connection.autoCommit = false
        }
    }
}
