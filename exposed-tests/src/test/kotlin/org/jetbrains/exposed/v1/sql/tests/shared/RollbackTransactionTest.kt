package org.jetbrains.exposed.v1.sql.tests.shared

import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.insert
import org.jetbrains.exposed.v1.sql.selectAll
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.sql.transactions.transaction
import org.jetbrains.exposed.v1.sql.transactions.transactionManager
import org.junit.Assume
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertContains
import kotlin.test.fail

class RollbackTransactionTest : DatabaseTestsBase() {

    @Test
    fun testRollbackWithoutSavepoints() {
        withTables(RollbackTable) {
            inTopLevelTransaction(db.transactionManager.defaultIsolationLevel) {
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
            inTopLevelTransaction(db.transactionManager.defaultIsolationLevel) {
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
}
