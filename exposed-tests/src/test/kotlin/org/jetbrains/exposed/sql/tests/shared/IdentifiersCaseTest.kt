package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test

class IdentifiersCaseTest : DatabaseTestsBase() {

    object LowerCaseTable : Table("lowercased") {
        val lower = integer("lowercased")
    }

    @Test fun testLowerCasedTable() {
        withDb {
            val ddl = LowerCaseTable.ddl.single()

            when (org.jetbrains.exposed.sql.tests.currentDialectTest) {
                is H2Dialect -> assertEquals("CREATE TABLE IF NOT EXISTS \"lowercased\" (\"lowercased\" INT NOT NULL)", ddl)
                is MysqlDialect, is SQLiteDialect, is PostgreSQLDialect ->
                    assertEquals("CREATE TABLE IF NOT EXISTS lowercased (lowercased INT NOT NULL)", ddl)
            }
        }
    }

}