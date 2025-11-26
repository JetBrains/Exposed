package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Tests that detect stack corruption by inspecting the stack DURING query execution.
 *
 * The key insight: We need to check the stack state WHILE a query is being executed,
 * not before or after, because withThreadLocalTransaction does push/pop internally.
 */
class TransactionStackCorruptionTest : DatabaseTestsBase() {

    object TestTable : LongIdTable("stack_test_table") {
        val name = varchar("name", 50)
    }

    /**
     * This interceptor checks the transaction stack state during query execution.
     * Without the fix, it will detect duplicate transactions on the stack.
     */
    class StackInspectorInterceptor : StatementInterceptor {
        var maxStackSize = AtomicInteger(0)
        var hasDuplicates = AtomicInteger(0)

        override fun beforeExecution(transaction: Transaction, context: org.jetbrains.exposed.v1.core.statements.StatementContext) {
            @OptIn(InternalApi::class)
            val size = ThreadLocalTransactionsStack.threadTransactions()!!.size
            maxStackSize.updateAndGet { current -> maxOf(current, size) }

            // Check for duplicates: if the same transaction appears multiple times
            @OptIn(InternalApi::class)
            val transactionIds = ThreadLocalTransactionsStack.threadTransactions()!!.map { it.transactionId }
            if (transactionIds.size != transactionIds.distinct().size) {
                hasDuplicates.incrementAndGet()
            }
        }
    }

    @Before
    fun before() {
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
    }

    /**
     * This test WILL FAIL without the fix because it detects duplicate
     * transactions on the stack DURING query execution.
     */
    @Test
    fun testNoDuplicateTransactionsOnStack() {
        val testDb = TestDB.H2_V2.connect()

        transaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        val inspector = StackInspectorInterceptor()

        try {
            runBlocking {
                suspendTransaction(db = testDb) {
                    // Register interceptor to inspect stack during query execution
                    registerInterceptor(inspector)

                    // Execute query - the interceptor will check stack state
                    val count = TestTable.selectAll().count()
                    assertEquals(1L, count)
                }
            }

            // Verify no duplicates were detected
            assertEquals(
                0, inspector.hasDuplicates.get(),
                "Duplicate transactions detected on stack! This means withThreadLocalTransaction " +
                    "pushed the same transaction that TransactionContextElement already pushed. " +
                    "Max stack size was: ${inspector.maxStackSize.get()}"
            )

            // The stack should never have more than 1 transaction in a non-nested scenario
            assertEquals(
                1, inspector.maxStackSize.get(),
                "Stack had ${inspector.maxStackSize.get()} transactions, but should only have 1. " +
                    "This indicates duplicate pushes are occurring."
            )
        } finally {
            transaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    /**
     * Test with multiple queries to increase chance of detecting the bug.
     */
    @Test
    fun testMultipleQueriesNoDuplicates() {
        val testDb = TestDB.H2_V2.connect()

        transaction(testDb) {
            SchemaUtils.create(TestTable)
            repeat(5) { i ->
                TestTable.insert { it[name] = "Entry $i" }
            }
        }

        val inspector = StackInspectorInterceptor()

        try {
            runBlocking {
                suspendTransaction(db = testDb) {
                    registerInterceptor(inspector)

                    // Multiple queries - each one could trigger duplicate push
                    repeat(10) {
                        val count = TestTable.selectAll().count()
                        assertEquals(5L, count)
                        yield()
                    }
                }
            }

            if (inspector.hasDuplicates.get() > 0) {
                fail(
                    "Detected ${inspector.hasDuplicates.get()} occurrences of duplicate transactions on stack. " +
                        "Max stack size: ${inspector.maxStackSize.get()}"
                )
            }

            assertEquals(
                1, inspector.maxStackSize.get(),
                "Stack should never exceed size 1 in non-nested transactions, but was ${inspector.maxStackSize.get()}"
            )
        } finally {
            transaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    /**
     * Test with parallel coroutines to detect race conditions.
     */
    @Test
    fun testParallelQueriesNoDuplicates() {
        val testDb = TestDB.H2_V2.connect()

        transaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        val inspector = StackInspectorInterceptor()
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        try {
            runBlocking {
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
            }

            if (inspector.hasDuplicates.get() > 0) {
                fail("Detected ${inspector.hasDuplicates.get()} occurrences of duplicate transactions on stack")
            }
        } finally {
            dispatcher.close()
            transaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    /**
     * Alternative test: Manually instrument withThreadLocalTransaction to detect
     * if it's being called with a transaction that's already on the stack.
     */
    @Test
    fun testWithThreadLocalTransactionNotCalledWithExistingTransaction() {
        val testDb = TestDB.H2_V2.connect()

        transaction(testDb) {
            SchemaUtils.create(TestTable)
            TestTable.insert { it[name] = "Test" }
        }

        try {
            runBlocking {
                suspendTransaction(db = testDb) {
                    // At this point, transaction is already on stack (pushed by TransactionContextElement)
                    @OptIn(InternalApi::class)
                    val currentTxOnStack = ThreadLocalTransactionsStack.getTransactionOrNull()

                    // When we execute a query, it will call withThreadLocalTransaction(this)
                    // which should check if 'this' is already on the stack
                    val count = TestTable.selectAll().count()

                    // After query, verify we still have the same transaction on top
                    @OptIn(InternalApi::class)
                    val txAfter = ThreadLocalTransactionsStack.getTransactionOrNull()

                    assertEquals(
                        currentTxOnStack?.transactionId, txAfter?.transactionId,
                        "Transaction on stack changed during query execution"
                    )
                    assertEquals(1L, count)
                }
            }

            // Verify stack is clean
            @OptIn(InternalApi::class)
            val isEmpty = ThreadLocalTransactionsStack.isEmpty()
            assertEquals(true, isEmpty, "Stack should be empty after transaction")
        } finally {
            transaction(testDb) {
                SchemaUtils.drop(TestTable)
            }
        }
    }
}
