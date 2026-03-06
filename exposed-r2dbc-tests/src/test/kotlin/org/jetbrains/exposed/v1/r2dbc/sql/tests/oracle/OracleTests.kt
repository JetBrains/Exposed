package org.jetbrains.exposed.v1.r2dbc.sql.tests.oracle

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OracleTests : R2dbcDatabaseTestsBase() {
    object Cities : Table("cities") {
        val id = integer("id")
        val name = varchar("name", 50)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testForUpdateSkipLockedSyntax() = runTest {
        val id = 1

        withTables(excludeSettings = TestDB.ALL - TestDB.ORACLE, Cities) {
            val cityName = "New York"
            Cities.insert {
                it[Cities.id] = id
                it[name] = cityName
            }
            commit()

            // Test standard FOR UPDATE
            val forUpdateQuery = Cities.selectAll().where { Cities.id eq id }.forUpdate(ForUpdateOption.ForUpdate)
            val forUpdateSql = forUpdateQuery.prepareSQL(this, false)
            assertTrue(forUpdateSql.contains("FOR UPDATE"), "Expected 'FOR UPDATE' in: $forUpdateSql")
            assertEquals(cityName, forUpdateQuery.map { it[Cities.name] }.single())

            // Test FOR UPDATE NOWAIT
            val forUpdateNoWaitQuery = Cities.selectAll().where { Cities.id eq id }.forUpdate(ForUpdateOption.Oracle.ForUpdateNoWait)
            val forUpdateNoWaitSql = forUpdateNoWaitQuery.prepareSQL(this, false)
            assertTrue(forUpdateNoWaitSql.contains("FOR UPDATE NOWAIT"), "Expected 'FOR UPDATE NOWAIT' in: $forUpdateNoWaitSql")
            assertEquals(cityName, forUpdateNoWaitQuery.map { it[Cities.name] }.single())

            // Test FOR UPDATE WAIT
            val forUpdateWaitQuery = Cities.selectAll().where { Cities.id eq id }.forUpdate(ForUpdateOption.Oracle.ForUpdateWait(5))
            val forUpdateWaitSql = forUpdateWaitQuery.prepareSQL(this, false)
            assertTrue(forUpdateWaitSql.contains("FOR UPDATE WAIT 5"), "Expected 'FOR UPDATE WAIT 5' in: $forUpdateWaitSql")
            assertEquals(cityName, forUpdateWaitQuery.map { it[Cities.name] }.single())

            // Test FOR UPDATE SKIP LOCKED
            val forUpdateSkipLockedQuery = Cities.selectAll().where { Cities.id eq id }.forUpdate(ForUpdateOption.Oracle.ForUpdateSkipLocked)
            val forUpdateSkipLockedSql = forUpdateSkipLockedQuery.prepareSQL(this, false)
            assertTrue(forUpdateSkipLockedSql.contains("FOR UPDATE SKIP LOCKED"), "Expected 'FOR UPDATE SKIP LOCKED' in: $forUpdateSkipLockedSql")
            assertEquals(cityName, forUpdateSkipLockedQuery.map { it[Cities.name] }.single())
        }
    }
}
