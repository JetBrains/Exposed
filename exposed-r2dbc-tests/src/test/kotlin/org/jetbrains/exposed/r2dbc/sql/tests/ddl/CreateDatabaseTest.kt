package org.jetbrains.exposed.r2dbc.sql.tests.ddl

import io.r2dbc.spi.R2dbcException
import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class CreateDatabaseTest : R2dbcDatabaseTestsBase() {

    @Test
    @Suppress("SwallowedException")
    fun testCreateAndDropDatabase() {
        // very unclear how the JDBC version passes with SQL SERVER
        // as CREATE DATABASE requires autoCommit on & JdbcTransaction starts with autoCommit off
        withDb(excludeSettings = listOf(TestDB.POSTGRESQL, TestDB.ORACLE, TestDB.SQLSERVER)) {
            val dbName = "jetbrains"
            try {
                SchemaUtils.dropDatabase(dbName)
            } catch (cause: R2dbcException) {
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
    fun testListDatabasesWithAutoCommit() {
        withDb(listOf(TestDB.POSTGRESQL, TestDB.SQLSERVER)) { testDb ->
            // Connection.setAutoCommit() should first commit the transaction if already active
            // But this does not seem to happen with SQL Server, leading to multi-statement tx errors
            // so we commit here to force a new transaction in autoCommit mode
            if (testDb == TestDB.SQLSERVER) connection.commit()
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
    fun testListDatabases() {
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
            assertTrue(dbName !in deleted)
        }
    }

    @Test
    fun testCreateAndDropDatabaseWithAutoCommit() {
        // PostgreSQL needs auto commit to be "ON" to allow create database statement
        withDb(listOf(TestDB.POSTGRESQL, TestDB.SQLSERVER)) { testDb ->
            // Connection.setAutoCommit() should first commit the transaction if already active
            // But this does not seem to happen with SQL Server, leading to multi-statement tx errors
            // so we commit here to force a new transaction in autoCommit mode
            if (testDb == TestDB.SQLSERVER) connection.commit()
            connection.setAutoCommit(true)
            val dbName = "jetbrains"
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
            connection.setAutoCommit(false)
        }
    }
}
