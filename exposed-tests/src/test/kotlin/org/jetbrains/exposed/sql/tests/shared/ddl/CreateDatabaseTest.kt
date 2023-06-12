package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test
import java.sql.SQLException

class CreateDatabaseTest : DatabaseTestsBase() {

    @Test
    fun `create database test`() {
        // PostgreSQL will be tested in the next test function
        withDb(excludeSettings = listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)) {
            val dbName = "jetbrains"
            try {
                SchemaUtils.dropDatabase(dbName)
            } catch (e: SQLException) {
                //ignore
            }
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
        }
    }

    @Test
    fun `create database test in postgreSQL`() {
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
