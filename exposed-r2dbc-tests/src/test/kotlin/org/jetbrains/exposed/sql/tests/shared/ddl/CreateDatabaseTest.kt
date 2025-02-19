package org.jetbrains.exposed.sql.tests.shared.ddl

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertFailsWith

class CreateDatabaseTest : R2dbcDatabaseTestsBase() {
    @Test
    @Suppress("SwallowedException")
    fun testCreateAndDropDatabase() = runTest {
        withDb(TestDB.ALL_H2 + TestDB.ALL_MYSQL_MARIADB) {
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
    fun testListDatabasesOracle() = runTest {
        withDb(TestDB.ORACLE) {
            assertFailsWith<IllegalStateException> {
                SchemaUtils.listDatabases()
            }
        }
    }

    @Test
    fun testListDatabasesWithAutoCommit() = runTest {
        withDb(listOf(TestDB.POSTGRESQL)) {
            connection.setAutoCommit(true)
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
            connection.setAutoCommit(false)
        }
    }

    @Test
    fun testListDatabases() = runTest {
        withDb(excludeSettings = listOf(TestDB.ORACLE, TestDB.POSTGRESQL, TestDB.SQLSERVER)) {
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
            println(deleted)
            assertTrue(dbName !in deleted)
        }
    }

    @Test
    fun testCreateAndDropDatabaseWithAutoCommit() = runTest {
        // PostgreSQL needs auto commit to be "ON" to allow create database statement
        withDb(listOf(TestDB.POSTGRESQL)) {
            connection.setAutoCommit(true)
            val dbName = "jetbrains"
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
            connection.setAutoCommit(false)
        }
    }
}
