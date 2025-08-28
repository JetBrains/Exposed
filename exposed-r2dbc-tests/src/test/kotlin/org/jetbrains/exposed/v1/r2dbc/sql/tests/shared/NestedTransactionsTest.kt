package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class NestedTransactionsTest : R2dbcDatabaseTestsBase() {
    private val db by lazy {
        R2dbcDatabase.connect(
            "r2dbc:h2:mem:///db1;DB_CLOSE_DELAY=-1;", "h2", "root", "",
            databaseConfig = R2dbcDatabaseConfig {
                useNestedTransactions = true
                defaultMaxAttempts = 1
            }
        )
    }

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

    @Test
    @Suppress("UseCheckOrError")
    fun `test outer suspendTransaction restored after nested suspendTransaction failed`() {
        withTables(DMLTestsData.Cities) {
            assertNotNull(TransactionManager.currentOrNull())

            try {
                inTopLevelSuspendTransaction(this.transactionIsolation) {
                    maxAttempts = 1
                    throw IllegalStateException("Should be rethrow")
                }
            } catch (e: Exception) {
                assertTrue(e is IllegalStateException)
            }

            assertNotNull(TransactionManager.currentOrNull())
        }
    }

    @Test
    fun testNestedTransactionNotCommittedAfterDatabaseFailure() = runTest {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"

        suspendTransaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        suspendTransaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelSuspendTransaction(db.transactionManager.defaultIsolationLevel!!, db = db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: R2dbcException) {
                assertContains(cause.toString(), fakeSQLString)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        suspendTransaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                suspendTransaction(db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: R2dbcException) {
                assertContains(cause.toString(), fakeSQLString)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        suspendTransaction(db) {
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testNestedTransactionNotCommittedAfterException() = runTest {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val exceptionMessage = "Failure!"

        suspendTransaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        suspendTransaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelSuspendTransaction(db.transactionManager.defaultIsolationLevel!!, db = db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    error(exceptionMessage)
                }
            } catch (cause: IllegalStateException) {
                assertContains(cause.toString(), exceptionMessage)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        suspendTransaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                suspendTransaction(db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    error(exceptionMessage)
                }
            } catch (cause: IllegalStateException) {
                assertContains(cause.toString(), exceptionMessage)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        suspendTransaction(db) {
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    private suspend fun assertSingleRecordInNewTransactionAndReset() = suspendTransaction(db) {
        val result = DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name]
        assertEquals("City A", result)
        DMLTestsData.Cities.deleteAll()
    }
}
