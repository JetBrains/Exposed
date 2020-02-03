package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class DatabaseTests : DatabaseTestsBase() {

    @Test
    fun `create database test`() {
        withDb() {
            val dbName = "jetbrains"
            SchemaUtils.createDatabase(dbName)
            SchemaUtils.dropDatabase(dbName)
        }
    }
}
