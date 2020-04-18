package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test

class ReplaceTests : DatabaseTestsBase() {
    // GitHub issue #98: Parameter index out of range when using Table.replace
    @Test
    fun testReplace01() {
        val NewAuth = object : Table() {
            val username = varchar("username", 16)
            val session = binary("session", 64)
            val timestamp = long("timestamp").default(0)
            val serverID = varchar("serverID", 64).default("")

            override val primaryKey = PrimaryKey(username)
        }
        // Only MySQL supp
        withTables(TestDB.values().toList() - listOf(TestDB.MYSQL, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG), NewAuth) {
            NewAuth.replace {
                it[username] = "username"
                it[session] = "session".toByteArray()
            }
        }
    }
}