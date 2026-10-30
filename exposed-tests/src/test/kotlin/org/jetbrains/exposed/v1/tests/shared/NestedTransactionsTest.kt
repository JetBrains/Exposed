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
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
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
                inTopLevelTransaction(transactionIsolation = this.transactionIsolation) {
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
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        transaction(db) {
            val outerTxId = this.transactionId

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelTransaction(db) {
                    val innerTxId = this.transactionId
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
            val outerTxId = this.transactionId

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                transaction(db) {
                    val innerTxId = this.transactionId
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
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val exceptionMessage = "Failure!"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        transaction(db) {
            val outerTxId = this.transactionId

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                inTopLevelTransaction(db) {
                    val innerTxId = this.transactionId
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
            val outerTxId = this.transactionId

            DMLTestsData.Cities.insert { it[name] = "City A" }
            assertEquals(1, DMLTestsData.Cities.selectAll().count())

            try {
                transaction(db) {
                    val innerTxId = this.transactionId
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

    @Test
    fun testNestedTransactionSetsSavepointOnlyWhenConnectionIsUsed() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        var connectionsOpened = 0
        val countingDb = Database.connect(
            getNewConnection = {
                connectionsOpened++
                DriverManager.getConnection("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;", "root", "")
            },
            databaseConfig = DatabaseConfig {
                useNestedTransactions = true
                // avoids resolving the default isolation level through a metadata connection
                defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
            }
        )

        transaction(countingDb) {
            transaction {
                rollback()
            }
            assertEquals(0, connectionsOpened)

            transaction {
                connection
            }
            assertEquals(1, connectionsOpened)
        }
    }

    @Test
    fun testRollbackOfIdleNestedTransactionUndoesItsInnerTransaction() {
        withTables(DMLTestsData.Cities, configure = { useNestedTransactions = true }) {
            DMLTestsData.Cities.insert { it[name] = "city1" }

            transaction {
                // runs no statements itself, so its savepoint is set only when the inner transaction needs the connection
                transaction {
                    DMLTestsData.Cities.insert { it[name] = "city2" }
                }
                assertEqualLists(listOf("city1", "city2"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
                rollback()
            }

            assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
        }
    }

    private fun assertSingleRecordInNewTransactionAndReset() = transaction(db) {
        val result = DMLTestsData.Cities.selectAll().single()[DMLTestsData.Cities.name]
        assertEquals("City A", result)
        DMLTestsData.Cities.deleteAll()
    }
}
