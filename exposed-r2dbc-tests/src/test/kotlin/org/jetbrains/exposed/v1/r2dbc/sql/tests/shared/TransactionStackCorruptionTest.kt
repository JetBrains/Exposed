package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.fail

class TransactionStackCorruptionTest : R2dbcDatabaseTestsBase() {

    object TestTable : LongIdTable("stack_test_table") {
        val name = varchar("name", 50)
    }

    class StackInspectorInterceptor : StatementInterceptor {
        var maxStackSize = AtomicInteger(0)
        var hasDuplicates = AtomicInteger(0)

        override fun beforeExecution(transaction: Transaction, context: StatementContext) {
            @OptIn(InternalApi::class)
            val size = ThreadLocalTransactionsStack.threadTransactions()!!.size
            maxStackSize.updateAndGet { current -> maxOf(current, size) }

            // Check for duplicates: if the same suspendTransaction appears multiple times
            @OptIn(InternalApi::class)
            val suspendTransactionIds = ThreadLocalTransactionsStack.threadTransactions()!!.map { it.transactionId }
            if (suspendTransactionIds.size != suspendTransactionIds.distinct().size) {
                hasDuplicates.incrementAndGet()
            }
        }
    }

    @BeforeEach
    fun before() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    @Disabled
    @Test
    fun testNoDuplicateTransactionsOnStack() = runTest {
        val testDb = TestDB.H2_V2.connect()

        suspendTransaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        val inspector = StackInspectorInterceptor()

        try {
            suspendTransaction(db = testDb) {
                // Register interceptor to inspect stack during query execution
                registerInterceptor(inspector)

                // Execute query - the interceptor will check stack state
                val count = TestTable.selectAll().count()
                assertEquals(1L, count)
            }

            // Verify no duplicates were detected
            assertEquals(
                0, inspector.hasDuplicates.get(),
                "Duplicate suspendTransactions detected on stack! This means withThreadLocalTransaction " +
                    "pushed the same suspendTransaction that TransactionContextElement already pushed. " +
                    "Max stack size was: ${inspector.maxStackSize.get()}"
            )

            // The stack should never have more than 1 suspendTransaction in a non-nested scenario
            assertEquals(
                1, inspector.maxStackSize.get(),
                "Stack had ${inspector.maxStackSize.get()} suspendTransactions, but should only have 1. " +
                    "This indicates duplicate pushes are occurring."
            )
        } finally {
            suspendTransaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Disabled
    @Test
    fun testMultipleQueriesNoDuplicates() = runTest {
        val testDb = TestDB.H2_V2.connect()

        suspendTransaction(testDb) {
            SchemaUtils.create(TestTable)
            repeat(5) { i ->
                TestTable.insert { it[name] = "Entry $i" }
            }
        }

        val inspector = StackInspectorInterceptor()

        try {
            suspendTransaction(db = testDb) {
                registerInterceptor(inspector)

                // Multiple queries - each one could trigger duplicate push
                repeat(10) {
                    val count = TestTable.selectAll().count()
                    assertEquals(5L, count)
                    yield()
                }
            }

            if (inspector.hasDuplicates.get() > 0) {
                fail(
                    "Detected ${inspector.hasDuplicates.get()} occurrences of duplicate suspendTransactions on stack. " +
                        "Max stack size: ${inspector.maxStackSize.get()}"
                )
            }

            assertEquals(
                1, inspector.maxStackSize.get(),
                "Stack should never exceed size 1 in non-nested suspendTransactions, but was ${inspector.maxStackSize.get()}"
            )
        } finally {
            suspendTransaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Disabled
    @Test
    fun testParallelQueriesNoDuplicates() = runTest {
        val testDb = TestDB.H2_V2.connect()

        suspendTransaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        val inspector = StackInspectorInterceptor()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        try {
            val jobs = List(20) { jobId ->
                launch(dispatcher) {
                    suspendTransaction(db = testDb) {
                        registerInterceptor(inspector)

                        val count = TestTable.selectAll().count()

                        // Force context switch
                        withContext(Dispatchers.IO) {
                            delay(1)
                        }

                        assertEquals(1L, count, "Job $jobId failed")
                    }
                }
            }

            jobs.joinAll()

            if (inspector.hasDuplicates.get() > 0) {
                fail("Detected ${inspector.hasDuplicates.get()} occurrences of duplicate suspendTransactions on stack")
            }
        } finally {
            dispatcher.close()
            suspendTransaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test
    fun testWithThreadLocalTransactionNotCalledWithExistingTransaction() = runTest {
        val testDb = TestDB.H2_V2.connect()

        suspendTransaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        try {
            suspendTransaction(db = testDb) {
                // At this point, suspendTransaction is already on stack (pushed by TransactionContextElement)
                @OptIn(InternalApi::class)
                val currentTxOnStack = ThreadLocalTransactionsStack.getTransactionOrNull()

                // When we execute a query, it will call withThreadLocalTransaction(this)
                // which should check if 'this' is already on the stack
                val count = TestTable.selectAll().count()

                // After query, verify we still have the same suspendTransaction on top
                @OptIn(InternalApi::class)
                val txAfter = ThreadLocalTransactionsStack.getTransactionOrNull()

                assertEquals(
                    currentTxOnStack?.transactionId, txAfter?.transactionId,
                    "Transaction on stack changed during query execution"
                )
                assertEquals(1L, count)
            }

            // Verify stack is clean
            @OptIn(InternalApi::class)
            val isEmpty = ThreadLocalTransactionsStack.isEmpty()
            assertEquals(true, isEmpty, "Stack should be empty after suspendTransaction")
        } finally {
            suspendTransaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }
}
