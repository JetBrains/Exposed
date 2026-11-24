package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class SuspendTransactionTests : DatabaseTestsBase() {
    private val uuid = Uuid.parseHexDash("b1dd54af-314f-4dac-9b8d-a6eacb825b61")

    object TestConflictTable : UuidTable("test_conflict") {
        val value = integer("value")
    }

    object ExampleTable : Table("suspend_nesting_example") {
        val value = text("value")
    }

    // equivalent to exposed-r2dbc-tests/PostgresqlTests.kt/testClosedSuspendTransaction()
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
            inTopLevelTransaction(transactionIsolation = TRANSACTION_SERIALIZABLE) {
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
        newSuspendedTransaction(null, transactionIsolation = TRANSACTION_SERIALIZABLE) {
            val current = TestConflictTable
                .selectAll()
                .where { TestConflictTable.id eq uuid }
                .forUpdate()
                .single()[TestConflictTable.value]

            delay((100..300).random().milliseconds)

            TestConflictTable.update({ TestConflictTable.id eq uuid }) {
                it[value] = current + 1
            }
        }
    }

    // https://github.com/JetBrains/Exposed/issues/1586
    @Test
    fun testNestedSuspendedTransactionNotCommittedWhenOuterRollsBackWithSavepoints() {
        withTables(ExampleTable, configure = { useNestedTransactions = true }) {
            runBlocking {
                try {
                    newSuspendedTransaction(db = db) {
                        val outerId = transactionId
                        ExampleTable.insert { it[value] = "outer" }

                        newSuspendedTransaction(db = db) {
                            val innerId = transactionId
                            assertNotEquals(outerId, innerId)
                            ExampleTable.insert { it[value] = "inner" }
                        }

                        error("outer transaction throws")
                    }
                    fail("Should have thrown an exception")
                } catch (_: IllegalStateException) {
                    // expected: the outer transaction threw
                }

                val rows: List<String> = newSuspendedTransaction(db = db) {
                    ExampleTable.selectAll().map { it[ExampleTable.value] }
                }
                assertEquals(0, rows.size)
            }
        }
    }

    // https://github.com/JetBrains/Exposed/issues/1586
    @Test
    fun testNestedSuspendedTransactionNotCommittedWhenOuterRollsBackWithoutSavepoints() {
        withTables(ExampleTable, configure = { useNestedTransactions = false }) {
            runBlocking {
                try {
                    newSuspendedTransaction(db = db) {
                        val outerId = transactionId
                        ExampleTable.insert { it[value] = "outer" }

                        newSuspendedTransaction(db = db) {
                            val innerId = transactionId
                            assertEquals(outerId, innerId)
                            ExampleTable.insert { it[value] = "inner" }
                        }

                        error("outer transaction throws")
                    }
                    fail("Should have thrown an exception")
                } catch (_: IllegalStateException) {
                    // expected: the outer transaction threw
                }

                val rows: List<String> = newSuspendedTransaction(db = db) {
                    ExampleTable.selectAll().map { it[ExampleTable.value] }
                }
                assertEquals(0, rows.size)
            }
        }
    }

    @Test
    fun testNestedSuspendedTransactionRolledBackToSavepointWhenInnerFails() {
        withTables(ExampleTable, configure = { useNestedTransactions = true }) {
            runBlocking {
                newSuspendedTransaction(db = db) {
                    ExampleTable.insert { it[value] = "outer" }

                    try {
                        newSuspendedTransaction(db = db) {
                            ExampleTable.insert { it[value] = "inner" }
                            error("inner transaction throws")
                        }
                    } catch (_: IllegalStateException) {
                        // expected: only the nested transaction is rolled back, the outer one continues
                    }
                }

                val rows: List<String> = newSuspendedTransaction(db = db) {
                    ExampleTable.selectAll().map { it[ExampleTable.value] }
                }
                assertEqualLists(rows, listOf("outer"))
            }
        }
    }

    @Test
    fun testNestedSuspendedTransactionsCommitWhenBothSucceed() {
        withTables(ExampleTable, configure = { useNestedTransactions = true }) {
            runBlocking {
                newSuspendedTransaction(db = db) {
                    ExampleTable.insert { it[value] = "outer" }

                    newSuspendedTransaction(db = db) {
                        ExampleTable.insert { it[value] = "inner" }
                    }
                }

                val rows: List<String> = newSuspendedTransaction(db = db) {
                    ExampleTable.selectAll().map { it[ExampleTable.value] }
                }
                assertEqualLists(rows, listOf("outer", "inner"))
            }
        }
    }
}
