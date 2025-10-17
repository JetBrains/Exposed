package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.junit.Assume
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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

    // suspend version of testNestedTransactions() above;
    // Outcome/Behavior of this test is equivalent to what would happen if 'newSuspendedTransaction' usages were
    // replace with 'inTopLevelTransaction', which is different to simply nesting new transactions.
    @Test
    fun testNestedSuspendTransactions() {
        withTables(excludeSettings = TestDB.ALL - TestDB.H2_V2, DMLTestsData.Cities) {
            assertTrue(DMLTestsData.Cities.selectAll().empty())

            DMLTestsData.Cities.insert {
                it[name] = "city1"
            }

            assertEquals(1L, DMLTestsData.Cities.selectAll().count())

            assertEqualLists(listOf("city1"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

            runBlocking {
                // unlike transaction + DbConfig in test above, these methods create new top level transactions with no commit of outer
                newSuspendedTransaction(Dispatchers.IO) {
                    DMLTestsData.Cities.insert {
                        it[name] = "city2"
                    }
                    // so, unlike test above, this transaction has no knowledge/commiting of "city1" record
                    assertEqualLists(listOf("city2"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                    // creates another new transaction with no commit of outer "city2" record
                    newSuspendedTransaction(Dispatchers.IO) {
                        DMLTestsData.Cities.insert {
                            it[name] = "city3"
                        }
                        // so, unlike test above, this transaction has no knowledge/commiting of "city1" OR "city2" record
                        assertEqualLists(listOf("city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
                    }
                    // so, unlike test above, this transaction has no knowledge/commiting of "city1" record
                    assertEqualLists(listOf("city2", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })

                    rollback() // so this only rolls back the first 'newSuspendedTransaction', not any nested
                }
            }
            // so, unlike test above, "nested" transaction did not undergo rollback
            assertEqualLists(listOf("city1", "city3"), DMLTestsData.Cities.selectAll().map { it[DMLTestsData.Cities.name] })
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
                inTopLevelTransaction(db) {
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

    // Compare to lines 147 - 165 in test above for diff in exception handling;
    // Placement of try-catch exception handling must differ compared to blocking transaction methods.
    @Test
    fun testNestedSuspendTransactionNotCommittedAfterDatabaseFailure() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        var exceptionCaughtByInner = false
        var exceptionCaughtByOuter = false

        try {
            runBlocking {
                try {
                    newSuspendedTransaction(db = db) {
                        val outerTxId = this.id

                        DMLTestsData.Cities.insert { it[name] = "City A" }
                        assertEquals(1, DMLTestsData.Cities.selectAll().count())

                        // when using blocking transaction methods, it is sufficient to wrap the culprit/failing operations
                        // in a try-catch block;
                        try {
                            newSuspendedTransaction(transactionIsolation = db.transactionManager.defaultIsolationLevel, db = db) {
                                val innerTxId = this.id
                                assertNotEquals(outerTxId, innerTxId)

                                DMLTestsData.Cities.insert { it[name] = "City B" }
                                exec("$fakeSQLString()")
                            }
                            fail("Should have thrown an exception")
                        } catch (cause: SQLException) {
                            // with suspend transaction methods, the exception will be caught, but will continue to propagate up
                            exceptionCaughtByInner = true
                            assertContains(cause.toString(), fakeSQLString)
                        }
                    }
                } catch (cause: SQLException) {
                    exceptionCaughtByOuter = true
                    assertContains(cause.toString(), fakeSQLString)
                }
            }
        } catch (cause: SQLException) {
            // so it is necessary to wrap the entire coroutine in a try-catch block; otherwise test will fail with inner exception
            assertContains(cause.toString(), fakeSQLString)
        }

        assertTrue(exceptionCaughtByInner)
        assertTrue(exceptionCaughtByOuter)

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
                inTopLevelTransaction(db) {
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

    // Compare to lines 267 - 284 in test above for diff in exception handling;
    // Placement of try-catch exception handling must differ compared to blocking transaction methods.
    @Test
    fun testNestedSuspendTransactionNotCommittedAfterException() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val exceptionMessage = "Failure!"

        transaction(db) {
            SchemaUtils.create(DMLTestsData.Cities)
        }

        var exceptionCaughtByInner = false
        var exceptionCaughtByOuter = false

        try {
            runBlocking {
                try {
                    newSuspendedTransaction(db = db) {
                        val outerTxId = this.id

                        DMLTestsData.Cities.insert { it[name] = "City A" }
                        assertEquals(1, DMLTestsData.Cities.selectAll().count())

                        // when using blocking transaction methods, it is sufficient to wrap the culprit/failing operations
                        // in a try-catch block;
                        try {
                            newSuspendedTransaction(transactionIsolation = db.transactionManager.defaultIsolationLevel, db = db) {
                                val innerTxId = this.id
                                assertNotEquals(outerTxId, innerTxId)

                                DMLTestsData.Cities.insert { it[name] = "City B" }
                                error(exceptionMessage)
                            }
                        } catch (cause: IllegalStateException) {
                            // with suspend transaction methods, the exception will be caught, but will continue to propagate up
                            exceptionCaughtByInner = true
                            assertContains(cause.toString(), exceptionMessage)
                        }
                    }
                } catch (cause: IllegalStateException) {
                    exceptionCaughtByOuter = true
                    assertContains(cause.toString(), exceptionMessage)
                }
            }
        } catch (cause: IllegalStateException) {
            // so it is necessary to wrap the entire coroutine in a try-catch block; otherwise test will fail with inner exception
            assertContains(cause.toString(), exceptionMessage)
        }

        assertTrue(exceptionCaughtByInner)
        assertTrue(exceptionCaughtByOuter)

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
