package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.fail

class RollbackTransactionTest : R2dbcDatabaseTestsBase() {
    object RollbackTable : IntIdTable("rollbackTable") {
        val value = varchar("value", 20)
    }

    @Test
    fun testRollbackWithoutSavepoints() {
        withTables(RollbackTable) {
            inTopLevelSuspendTransaction {
                maxAttempts = 1
                RollbackTable.insert { it[value] = "before-dummy" }
                suspendTransaction {
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
            inTopLevelSuspendTransaction {
                maxAttempts = 1
                RollbackTable.insert { it[value] = "before-dummy" }
                suspendTransaction {
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
    fun testRollbackWithoutSavepointsTriggeredByExceptions() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TestDB.H2_V2.connect()

        suspendTransaction {
            SchemaUtils.create(RollbackTable)
        }

        // database exception triggers rollback from inner to outer tx
        suspendTransaction {
            val fakeSQLString = "BROKEN_SQL_THAT_CAUSES_EXCEPTION"
            val outerTxId = this.transactionId

            RollbackTable.insert { it[value] = "City A" }
            assertEquals(1, RollbackTable.selectAll().count())

            try {
                suspendTransaction {
                    val innerTxId = this.transactionId
                    assertEquals(outerTxId, innerTxId)

                    RollbackTable.insert { it[value] = "City B" }
                    exec("$fakeSQLString()")
                }
                fail("Should have thrown an exception")
            } catch (cause: R2dbcException) {
                assertContains(cause.message.toString(), fakeSQLString)
            }

            assertEquals(0, RollbackTable.selectAll().count())
        }

        // non-db exception propagates from inner to outer without rollback and is handled, if caught.
        // if not caught & exception propagates all the way to outer tx, full rollback occurs (as always).
        suspendTransaction {
            val outerTxId = this.transactionId

            RollbackTable.insert { it[value] = "City A" }
            assertEquals(1, RollbackTable.selectAll().count())

            try {
                suspendTransaction(db) {
                    val innerTxId = this.transactionId
                    assertEquals(outerTxId, innerTxId)

                    RollbackTable.insert { it[value] = "City B" }
                    error("Failure")
                }
            } catch (cause: IllegalStateException) {
                assertContains(cause.toString(), "Failure")
            }

            assertEquals(2, RollbackTable.selectAll().count())
        }

        suspendTransaction {
            SchemaUtils.drop(RollbackTable)
        }
    }
}
