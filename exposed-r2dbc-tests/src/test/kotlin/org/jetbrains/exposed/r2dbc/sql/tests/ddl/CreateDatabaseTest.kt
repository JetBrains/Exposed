package org.jetbrains.exposed.r2dbc.sql.tests.ddl

import org.jetbrains.exposed.r2dbc.sql.SchemaUtils
import org.jetbrains.exposed.r2dbc.sql.statements.R2dbcConnectionImpl
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertFailsWith

class CreateDatabaseTest : R2dbcDatabaseTestsBase() {

    @Test
    fun testListDatabases() {
        withDb(listOf(TestDB.POSTGRESQL, TestDB.SQLSERVER)) {
            println(SchemaUtils.listDatabases())
        }
    }

    @Test
    @Suppress("SwallowedException")
    fun testCreateAndDropDatabase() {
        withDb(excludeSettings = listOf(TestDB.POSTGRESQL, TestDB.ORACLE)) {
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
    fun testListDatabasesWithAutoCommit() {
        withDb(listOf(TestDB.POSTGRESQL, TestDB.SQLSERVER)) {
            (connection as R2dbcConnectionImpl).setAutoCommit(true)

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

            (connection as R2dbcConnectionImpl).setAutoCommit(false)
        }
    }
}
