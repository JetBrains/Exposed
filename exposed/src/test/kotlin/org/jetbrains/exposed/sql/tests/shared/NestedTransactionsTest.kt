package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertTrue

class NestedTransactionsTest : DatabaseTestsBase() {

    @Test
    fun testNestedTransactions() {
        withTables(DMLTestsData.Cities) {
            try {
                db.useNestedTransactions = true
                assertTrue(DMLTestsData.Cities.selectAll().empty())

                DMLTestsData.Cities.insert {
                    it[DMLTestsData.Cities.name] = "city1"
                }

                assertEquals(1, DMLTestsData.Cities.selectAll().count())

                assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                transaction {
                    DMLTestsData.Cities.insert {
                        it[DMLTestsData.Cities.name] = "city2"
                    }
                    assertEqualLists(listOf("city1", "city2"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                    transaction {
                        DMLTestsData.Cities.insert {
                            it[DMLTestsData.Cities.name] = "city3"
                        }
                        assertEqualLists(listOf("city1", "city2", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
                    }

                    assertEqualLists(listOf("city1", "city2", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                    rollback()
                }

                assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
            } finally {
                db.useNestedTransactions = false
            }
        }
    }
}