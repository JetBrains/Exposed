package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.Test

class RollbackTransactionTest : DatabaseTestsBase() {

    @Test
    fun testRollbackWithoutSavepoints() {
        withTables(RollbackTable) {
            inTopLevelTransaction(db.transactionManager.defaultIsolationLevel) {
                maxAttempts = 1
                RollbackTable.insert { it[RollbackTable.value] = "before-dummy" }
                transaction {
                    assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                    RollbackTable.insert { it[RollbackTable.value] = "inner-dummy" }
                }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
                RollbackTable.insert { it[RollbackTable.value] = "after-dummy" }
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
                RollbackTable.insert { it[RollbackTable.value] = "before-dummy" }
                transaction {
                    assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                    RollbackTable.insert { it[RollbackTable.value] = "inner-dummy" }
                    rollback()
                }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
                assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
                RollbackTable.insert { it[RollbackTable.value] = "after-dummy" }
                assertEquals(1L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
                rollback()
            }
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "before-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "inner-dummy" }.count())
            assertEquals(0L, RollbackTable.selectAll().where { RollbackTable.value eq "after-dummy" }.count())
        }
    }
}
