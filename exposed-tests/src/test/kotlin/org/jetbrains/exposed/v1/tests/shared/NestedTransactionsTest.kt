package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.junit.Assume
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class NestedTransactionsTest : DatabaseTestsBase() {
    private val db by lazy {
        Database.connect(
            "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "org.h2.Driver", "root", "",
            databaseConfig = DatabaseConfig {
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

            transaction {
                DMLTestsData.Cities.insert {
                    it[name] = "city2"
                }
                assertEqualLists(listOf("city1", "city2"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                transaction {
                    DMLTestsData.Cities.insert {
                        it[name] = "city3"
                    }
                    assertEqualLists(listOf("city1", "city2", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
                }

                assertEqualLists(listOf("city1", "city2", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                rollback()
            }

            assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
        }
    }

    @Test
    @Suppress("UseCheckOrError")
    fun `test outer transaction restored after nested transaction failed`() {
        withTables(DMLTestsData.Cities) {
            assertNotNull(TransactionManager.currentOrNull())

            try {
                inTopLevelTransaction(this.transactionIsolation) {
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
    fun testNestedTransactionNotCommittedAfterDatabaseFailure() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        transaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, db = db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: SQLException) {
                assertContains(cause.toString(), fakeSQLString)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        transaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                transaction(db) {
                    val innerTxId = this.id
                    assertNotEquals(outerTxId, innerTxId)

                    DMLTestsData.Cities.insert { it[name] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: SQLException) {
                assertContains(cause.toString(), fakeSQLString)
            }
        }

        assertSingleRecordInNewTransactionAndReset()

        transaction(db) {
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    @Test
    fun testNestedTransactionNotCommittedAfterException() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val exceptionMessage = "Failure!"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        transaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, db = db) {
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

        transaction(db) {
            val outerTxId = this.id

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                transaction(db) {
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

        transaction(db) {
            SchemaUtils.drop(DMLTestsData.Cities)
        }
    }

    private fun assertSingleRecordInNewTransactionAndReset() = transaction(db) {
        val result = DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name]
        assertEquals("City A", result)
        DMLTestsData.Cities.deleteAll()
    }
}
