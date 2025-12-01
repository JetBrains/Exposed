package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import io.r2dbc.spi.R2dbcException
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CreateDatabaseTest : R2dbcDatabaseTestsBase() {

    @Test
    @Suppress("SwallowedException")
    fun testCreateAndDropDatabase() {
        // very unclear how the JDBC version passes with SQL SERVER
        // as CREATE DATABASE requires autoCommit on & JdbcTransaction starts with autoCommit off
        withDb(excludeSettings = TestDB.ALL_POSTGRES + TestDB.ORACLE) { testDb ->
            // Connection.setAutoCommit() should first commit the transaction if already active
            // But this does not seem to happen with SQL Server, leading to multi-statement tx errors
            // so we commit here to force a new transaction in autoCommit mode
            if (testDb == TestDB.SQLSERVER) connection().commit()

            val dbName = "jetbrains"
            try {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            } catch (cause: R2dbcException) {
                // ignore
            }
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createDatabase(dbName)
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
        }
    }

    @Test
    fun testListDatabasesOracle() {
        withDb(TestDB.ORACLE) {
            assertFailsWith<IllegalStateException> {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            }
        }
    }

    @Test
    fun testListDatabasesWithAutoCommit() {
        withDb(TestDB.ALL_POSTGRES + TestDB.SQLSERVER) { testDb ->
            // Connection.setAutoCommit() should first commit the transaction if already active
            // But this does not seem to happen with SQL Server, leading to multi-statement tx errors
            // so we commit here to force a new transaction in autoCommit mode
            if (testDb == TestDB.SQLSERVER) connection().commit()

            connection().setAutoCommit(true)
            val dbName = "jetbrains"
            val initial = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            if (dbName in initial) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            }

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createDatabase(dbName)
            val created = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            val deleted = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
            connection().setAutoCommit(false)
        }
    }

    @Test
    fun testListDatabases() {
        withDb(excludeSettings = TestDB.ALL_POSTGRES + TestDB.SQLSERVER + TestDB.ORACLE) {
            val dbName = "jetbrains"
            val initial = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            if (dbName in initial) {
                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            }

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createDatabase(dbName)
            val created = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            assertTrue(dbName in created)
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            val deleted = org.jetbrains.exposed.v1.r2dbc.SchemaUtils.listDatabases()
            assertTrue(dbName !in deleted)
        }
    }

    @Test
    fun testCreateAndDropDatabaseWithAutoCommit() {
        // PostgreSQL needs auto commit to be "ON" to allow create database statement
        withDb(TestDB.ALL_POSTGRES) { testDb ->
            connection().setAutoCommit(true)
            val dbName = "jetbrains"
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.createDatabase(dbName)
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.dropDatabase(dbName)
            connection().setAutoCommit(false)
        }
    }
}
