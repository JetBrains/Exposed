package org.jetbrains.exposed.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.r2dbc.sql.transactions.suspendTransaction
import org.junit.Test

class NestedTransactionsTest : R2dbcDatabaseTestsBase() {
    @Test
    fun testNestedTransactions() {
        withTables(DMLTestsData.Cities, configure = { useNestedTransactions = true }) {
            assertTrue(DMLTestsData.Cities.selectAll().empty())

            DMLTestsData.Cities.insert {
                it[name] = "city1"
            }

            assertEquals(1L, DMLTestsData.Cities.selectAll().count())

            assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

            suspendTransaction {
                DMLTestsData.Cities.insert {
                    it[name] = "city2"
                }
                assertEqualLists(
                    listOf("city1", "city2"),
                    DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] }
                )

                suspendTransaction {
                    DMLTestsData.Cities.insert {
                        it[name] = "city3"
                    }
                    assertEqualLists(
                        listOf("city1", "city2", "city3"),
                        DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] }
                    )
                }

                assertEqualLists(
                    listOf("city1", "city2", "city3"),
                    DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] }
                )

                rollback()
            }

            assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
        }
    }

    // TODO there is currently no way to manually declare that a single nested transaction should not be nested
//    @Test
//    @Suppress("UseCheckOrError")
//    fun `test outer suspendTransaction restored after nested suspendTransaction failed`() {
//        withTables(DMLTestsData.Cities) {
//            assertNotNull(TransactionManager.currentOrNull())
//
//            try {
//                inTopLevelTransaction(this.transactionIsolation) {
//                    maxAttempts = 1
//                    throw IllegalStateException("Should be rethrow")
//                }
//            } catch (e: Exception) {
//                assertTrue(e is IllegalStateException)
//            }
//
//            assertNotNull(TransactionManager.currentOrNull())
//        }
//    }

//    @Test
//    fun testNestedTransactionNotCommittedAfterDatabaseFailure() = runTest {
//        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
//
//        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"
//
//        suspendTransaction(db = db) {
//            SchemaUtils.create(DMLTestsData.Cities)
//        }
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            DMLTestsData.Cities.insert { it[name] = "City A" }
//            assertEquals(1, DMLTestsData.Cities.selectAll().count())
//
//            try {
//                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, db = db) {
//                    val innerTxId = this.id
//                    assertNotEquals(outerTxId, innerTxId)
//
//                    DMLTestsData.Cities.insert { it[name] = "City B" }
//                    exec("$fakeSQLString()")
//                }
//                fail("Should have thrown an exception")
//            } catch (cause: SQLException) {
//                assertContains(cause.toString(), fakeSQLString)
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            DMLTestsData.Cities.insert { it[name] = "City A" }
//            assertEquals(1, DMLTestsData.Cities.selectAll().count())
//
//            try {
//                suspendTransaction(db = db) {
//                    val innerTxId = this.id
//                    assertNotEquals(outerTxId, innerTxId)
//
//                    DMLTestsData.Cities.insert { it[name] = "City B" }
//                    exec("$fakeSQLString()")
//                }
//                fail("Should have thrown an exception")
//            } catch (cause: SQLException) {
//                assertContains(cause.toString(), fakeSQLString)
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            SchemaUtils.drop(DMLTestsData.Cities)
//        }
//    }

//    @Test
//    fun testNestedTransactionNotCommittedAfterException() = runTest {
//        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
//
//        val exceptionMessage = "Failure!"
//
//        suspendTransaction(db = db) {
//            SchemaUtils.create(DMLTestsData.Cities)
//        }
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            DMLTestsData.Cities.insert { it[name] = "City A" }
//            assertEquals(1, DMLTestsData.Cities.selectAll().count())
//
//            try {
//                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, db = db) {
//                    val innerTxId = this.id
//                    assertNotEquals(outerTxId, innerTxId)
//
//                    DMLTestsData.Cities.insert { it[name] = "City B" }
//                    error(exceptionMessage)
//                }
//            } catch (cause: IllegalStateException) {
//                assertContains(cause.toString(), exceptionMessage)
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            val outerTxId = this.id
//
//            DMLTestsData.Cities.insert { it[name] = "City A" }
//            assertEquals(1, DMLTestsData.Cities.selectAll().count())
//
//            try {
//                suspendTransaction(db = db) {
//                    val innerTxId = this.id
//                    assertNotEquals(outerTxId, innerTxId)
//
//                    DMLTestsData.Cities.insert { it[name] = "City B" }
//                    error(exceptionMessage)
//                }
//            } catch (cause: IllegalStateException) {
//                assertContains(cause.toString(), exceptionMessage)
//            }
//        }
//
//        assertSingleRecordInNewTransactionAndReset()
//
//        suspendTransaction(db = db) {
//            SchemaUtils.drop(DMLTestsData.Cities)
//        }
//    }
}
