package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Test
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.util.*

class SuspendTransactionTests : DatabaseTestsBase() {
    private val uuid = UUID.fromString("b1dd54af-314f-4dac-9b8d-a6eacb825b61")

    object TestConflictTable : UUIDTable("test_conflict") {
        val value = integer("value")
    }

    // TODO Make similar test for R2DBC. The first attempt to do that is failed, probably R2DBC has similar issue.¬
    @Test
    fun testClosedSuspendTransaction() {
        withTables(
            // Test is quite flaky by unknown yet reason. Locally it works without problem, but fails on CI.
            excludeSettings = TestDB.ALL - TestDB.POSTGRESQL,
            TestConflictTable,
            configure = {
                defaultMaxAttempts = 20
            }
        ) {
            inTopLevelTransaction(TRANSACTION_SERIALIZABLE) {
                TestConflictTable.insert {
                    it[id] = uuid
                    it[value] = 0
                }
            }

            val concurrentTransactions = 3

            runBlocking {
                List(concurrentTransactions) {
                    launch(Dispatchers.IO) {
                        runExposedTransaction()
                    }
                }.joinAll()
            }
            val entry = TestConflictTable.selectAll().first()
            assertEquals(3, entry[TestConflictTable.value])
        }
    }

    private suspend fun runExposedTransaction() {
        newSuspendedTransaction(Dispatchers.IO, transactionIsolation = TRANSACTION_SERIALIZABLE) {
            val current = TestConflictTable
                .selectAll()
                .where({ TestConflictTable.id eq uuid })
                .forUpdate()
                .single()[TestConflictTable.value]

            delay((100..300).random().toLong())

            TestConflictTable.update({ TestConflictTable.id eq uuid }) {
                it[value] = current + 1
            }
        }
    }
}
