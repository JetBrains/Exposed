package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Assume
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.fail

class RollbackTransactionTest : DatabaseTestsBase() {

    @Test
    fun testRollbackWithoutSavepoints() {
        withTables(RollbackTable) {
            inTopLevelTransaction {
                maxAttempts = 1
                RollbackTable.insert { it[value] = "before-dummy" }
                transaction {
                    assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                    RollbackTable.insert { it[value] = "inner-dummy" }
                }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
                RollbackTable.insert { it[value] = "after-dummy" }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
                rollback()
            }
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
        }
    }

    @Test
    fun testRollbackWithSavepoints() {
        withTables(RollbackTable, configure = { useNestedTransactions = true }) {
            inTopLevelTransaction {
                maxAttempts = 1
                RollbackTable.insert { it[value] = "before-dummy" }
                transaction {
                    assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                    RollbackTable.insert { it[value] = "inner-dummy" }
                    rollback()
                }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
                RollbackTable.insert { it[value] = "after-dummy" }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
                rollback()
            }
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
        }
    }

    @Test
    fun testRollbackWithoutSavepointsTriggeredByExceptions() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TestDB.H2_V2.connect()

        transaction {
            SchemaUtils.create(RollbackTable)
        }

        // database exception triggers rollback from inner to outer tx
        transaction {
            val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"
            val outerTxId = this.id

            RollbackTable.insert { it[value] = "City A" }
            assertEquals(1, RollbackTable.selectAll().count())

            try {
                transaction {
                    val innerTxId = this.id
                    assertEquals(outerTxId, innerTxId)

                    RollbackTable.insert { it[value] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: SQLException) {
                assertContains(cause.toString(), fakeSQLString)
            }

            assertEquals(0, RollbackTable.selectAll().count())
        }

        // non-db exception propagates from inner to outer without rollback and is handled, if caught.
        // if not caught & exception propagates all the way to outer tx, full rollback occurs (as always).
        transaction {
            val outerTxId = this.id

            RollbackTable.insert { it[value] = "City A" }
            assertEquals(1, RollbackTable.selectAll().count())

            try {
                transaction(db) {
                    val innerTxId = this.id
                    assertEquals(outerTxId, innerTxId)

                    RollbackTable.insert { it[value] = "City B" }
                    error("Failure")
                }
            } catch (cause: IllegalStateException) {
                assertContains(cause.toString(), "Failure")
            }

            assertEquals(2, RollbackTable.selectAll().count())
        }

        transaction {
            SchemaUtils.drop(RollbackTable)
        }
    }

    // suspend (parent-child) transaction version of test above;
    // note difference with how exception handling affects rollback.
    @Test
    fun testRollbackWithoutSavepointsTriggeredByExceptionsFromSuspendTransactions() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TestDB.H2_V2.connect()

        transaction {
            SchemaUtils.create(RollbackTable)
        }

        var exceptionCaughtByChild = false
        var exceptionCaughtByParent = false

        val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"
        // database exception also triggers rollback from inner to outer tx;
        // but exception also propagated up to coroutine block even though caught by child exception handling
        try {
            runBlocking {
                try {
                    newSuspendedTransaction {
                        val outerTxId = this.id

                        RollbackTable.insert { it[value] = "City A" }
                        assertEquals(1, RollbackTable.selectAll().count())

                        try {
                            withSuspendTransaction {
                                val innerTxId = this.id
                                assertEquals(outerTxId, innerTxId)

                                RollbackTable.insert { it[value] = "City B" }
                                exec("$fakeSQLString()")
                            }
                            fail("Should have thrown an exception")
                        } catch (cause: SQLException) {
                            exceptionCaughtByChild = true
                            assertContains(cause.toString(), fakeSQLString)
                        }

                        // SO ANY OPERATIONS AFTER NESTED WILL NOT BE REACHED
                        fail("Caught exception should have been rethrown to parent")
                    }
                } catch (cause: SQLException) {
                    exceptionCaughtByParent = true
                    assertContains(cause.toString(), fakeSQLString)
                }
            }
        } catch (cause: SQLException) {
            assertContains(cause.toString(), fakeSQLString)
        }

        assertTrue(exceptionCaughtByChild)
        assertTrue(exceptionCaughtByParent)

        // BUT child exception still successfully triggered rollback to parent
        transaction {
            assertEquals(0, RollbackTable.selectAll().count())
        }

        exceptionCaughtByChild = false
        exceptionCaughtByParent = false

        // UNLIKE TEST ABOVE non-db exception also propagates from inner to outer, triggering full rollback.
        try {
            runBlocking {
                try {
                    newSuspendedTransaction {
                        val outerTxId = this.id

                        RollbackTable.insert { it[value] = "City A" }
                        assertEquals(1, RollbackTable.selectAll().count())

                        try {
                            withSuspendTransaction {
                                val innerTxId = this.id
                                assertEquals(outerTxId, innerTxId)

                                RollbackTable.insert { it[value] = "City B" }
                                error("Failure")
                            }
                        } catch (cause: IllegalStateException) {
                            // UNLIKE TEST ABOVE, catching/handling non-db exception does not affect rollback
                            exceptionCaughtByChild = true
                            assertContains(cause.toString(), "Failure")
                        }

                        // SO ANY OPERATIONS AFTER NESTED WILL NOT BE REACHED
                        fail("Caught exception should have been rethrown to parent")
                    }
                } catch (cause: IllegalStateException) {
                    exceptionCaughtByParent = true
                    assertContains(cause.toString(), "Failure")
                }
            }
        } catch (cause: IllegalStateException) {
            assertContains(cause.toString(), "Failure")
        }

        assertTrue(exceptionCaughtByChild)
        assertTrue(exceptionCaughtByParent)

        // UNLIKE TEST ABOVE, because child exception still bubbles up, full rollback is inevitable
        transaction {
            assertEquals(0, RollbackTable.selectAll().count())
        }

        transaction {
            SchemaUtils.drop(RollbackTable)
        }
    }
}
