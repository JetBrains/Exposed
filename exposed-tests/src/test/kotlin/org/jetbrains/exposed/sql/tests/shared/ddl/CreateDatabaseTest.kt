package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test

class CreateDatabaseTest : DatabaseTestsBase() {

    @Test
    fun `create database test`() {
        // PostgreSQL will be tested in the next test function
        // DB2:create database in db2 is a clp command, thus it cannot run in jdbc
        withDb(excludeSettings = listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.DB2)) {
            val dbName = "jetbrains"
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
