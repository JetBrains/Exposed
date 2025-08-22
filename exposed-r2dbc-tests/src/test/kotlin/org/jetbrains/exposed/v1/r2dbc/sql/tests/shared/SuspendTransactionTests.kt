package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.Test
import java.util.UUID

class SuspendTransactionTests : R2dbcDatabaseTestsBase() {
    private val uuid = UUID.fromString("b1dd54af-314f-4dac-9b8d-a6eacb825b61")

    object TestConflictTable : UUIDTable("test_conflict") {
        val value = integer("value")
    }

    @Test
    fun testClosedSuspendTransaction() {
        withTables(
            excludeSettings = TestDB.ALL - TestDB.POSTGRESQL,
            TestConflictTable,
            configure = {
                defaultMaxAttempts = 20
            }
        ) {
            inTopLevelSuspendTransaction(IsolationLevel.SERIALIZABLE) {
                TestConflictTable.insert {
                    it[id] = uuid
                    it[value] = 0
                }
            }

            val concurrentTransactions = 3

            withContext(Dispatchers.IO) {
                List(concurrentTransactions) {
                    launch {
                        runExposedTransaction()
                    }
                }.joinAll()
            }
            val entry = TestConflictTable.selectAll().first()
            assertEquals(3, entry[TestConflictTable.value])
        }
    }

    private suspend fun runExposedTransaction() {
        // how to pass context... withContext()?
        suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
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
