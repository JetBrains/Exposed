package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.dml.Cities
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertNotNull

class NestedTransactionsTest : DatabaseTestsBase() {

    @Test
    fun testNestedTransactions() {
        withTables(Cities) {
            try {
                db.useNestedTransactions = true
                assertTrue(Cities.selectAll().empty())

                Cities.insert { it[name] = "city1" }

                assertEquals(1L, Cities.selectAll().count())

                assertEqualLists(listOf("city1"), Cities.selectAll().map { it[Cities.name] })

                transaction {
                    Cities.insert { it[Cities.name] = "city2" }
                    assertEqualLists(listOf("city1", "city2"), Cities.selectAll().map { it[Cities.name] })

                    transaction {
                        Cities.insert { it[name] = "city3" }
                        assertEqualLists(listOf("city1", "city2", "city3"), Cities.selectAll().map { it[Cities.name] })
                    }

                    assertEqualLists(listOf("city1", "city2", "city3"), Cities.selectAll().map { it[Cities.name] })

                    rollback()
                }

                assertEqualLists(listOf("city1"), Cities.selectAll().map { it[Cities.name] })
            } finally {
                db.useNestedTransactions = false
            }
        }
    }

    @Test
    fun `test outer transaction restored after nested transaction failed`() {
        withTables(Cities) {
            assertNotNull(TransactionManager.currentOrNull())

            try {
                inTopLevelTransaction(this.transactionIsolation, 1) {
                    throw IllegalStateException("Should be rethrow")
                }
            } catch (e: Exception) {
                assertTrue(e is IllegalStateException)
            }

            assertNotNull(TransactionManager.currentOrNull())
        }
    }
}
