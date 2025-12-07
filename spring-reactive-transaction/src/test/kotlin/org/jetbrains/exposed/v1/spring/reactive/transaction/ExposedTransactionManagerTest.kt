package org.jetbrains.exposed.v1.spring.reactive.transaction

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import org.springframework.test.annotation.Commit
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith

open class ExposedTransactionManagerTest : SpringReactiveTransactionTestBase() {

    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    private suspend fun T1.insertRandom() {
        insert {
            it[c1] = Random().nextInt().toString()
        }
    }

    @OptIn(InternalApi::class)
    @BeforeTest
    fun beforeTest() = runTest {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }

        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
        println("\n-----------STARTING TEST-----------")
    }

    @OptIn(InternalApi::class)
    @AfterTest
    fun afterTest() = runTest {
        println("\n-----------FINISHED TEST-----------")
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }

        // TODO - this should not be done, but transaction is not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

//    @Transactional // see [runTestWithMockTransactional]
    @Commit
    @RepeatedTest(5)
    open fun testConnection() = runTestWithMockTransactional {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
    }

//    @Transactional // see [runTestWithMockTransactional]
    @Commit
    @RepeatedTest(5)
    open fun testConnection2() = runTestWithMockTransactional {
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }
        assertEquals(rnd, T1.selectAll().single()[T1.c1])
    }

    @RepeatedTest(5)
    @Commit
    open fun testConnectionCombineWithExposedTransaction() = runTest {
        suspendTransaction {
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }
            assertEquals(rnd, T1.selectAll().single()[T1.c1])

            this@ExposedTransactionManagerTest.transactionManager.execute {
                T1.insertRandom()
                assertEquals(2, T1.selectAll().count())
            }
        }
    }

    // TODO - This (& only this test?) fails because of line 114 in suspendTransaction();
    // If the line is reverted to original, it passes -> ThreadLocalTransactionsStack.getTransactionOrNull(databaseToUse)
    @Disabled
    @RepeatedTest(5)
    @Commit
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionCombineWithExposedTransaction2() = runTestWithMockTransactional {
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }
        assertEquals(rnd, T1.selectAll().single()[T1.c1])

        suspendTransaction {
            T1.insertRandom()
            assertEquals(2, T1.selectAll().count())
        }
    }

    /**
     * Test for Propagation.NESTED
     * Execute within a nested transaction if a current transaction exists, behave like REQUIRED otherwise.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithNestedTransactionCommit() = runTestWithMockTransactional {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
            T1.insertRandom()
            assertEquals(2, T1.selectAll().count())
        }
        assertEquals(2, T1.selectAll().count())
    }

    /**
     * Test for Propagation.NESTED with inner roll-back
     * The nested transaction will be roll-back only inner transaction when the transaction marks as rollback.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithNestedTransactionInnerRollback() = runTestWithMockTransactional {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) { status ->
            T1.insertRandom()
            assertEquals(2, T1.selectAll().count())
            status.setRollbackOnly()
        }
        assertEquals(1, T1.selectAll().count())
    }

    /**
     * Test for Propagation.NESTED with outer roll-back
     * The nested transaction will be roll-back entire transaction when the transaction marks as rollback.
     */
    @RepeatedTest(5)
    fun testConnectionWithNestedTransactionOuterRollback() = runTest {
        transactionManager.execute {
            T1.insertRandom()
            assertEquals(1, T1.selectAll().count())
            it.setRollbackOnly()

            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                T1.insertRandom()
                assertEquals(2, T1.selectAll().count())
            }
            assertEquals(2, T1.selectAll().count())
        }

        transactionManager.execute {
            assertEquals(0, T1.selectAll().count())
        }
    }

    // TODO
    /**
     * Test for Propagation.REQUIRES_NEW
     * Create a new transaction, and suspend the current transaction if one exists.
     */
    @Disabled("After doResume(), TransactionManager.current() in count() returns wrong unpopped transaction from thread-switch")
    @RepeatedTest(1)
    //    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionWithRequiresNew() = runTestWithMockTransactional {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            assertEquals(0, T1.selectAll().count())
            T1.insertRandom()
            assertEquals(1, T1.selectAll().count())
        }
        assertEquals(2, T1.selectAll().count())
    }

    // TODO
    /**
     * Test for Propagation.REQUIRES_NEW with inner transaction roll-back
     * The inner transaction will be roll-back only inner transaction when the transaction marks as rollback.
     * And since isolation level is READ_COMMITTED, the inner transaction can't see the changes of outer transaction.
     */
    @Disabled("After doResume(), TransactionManager.current() in count() returns wrong unpopped transaction from thread-switch")
    @RepeatedTest(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() = runTest {
        transactionManager.execute {
            T1.insertRandom()
            assertEquals(1, T1.selectAll().count())
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                T1.insertRandom()
                assertEquals(1, T1.selectAll().count())
                it.setRollbackOnly()
            }
            assertEquals(1, T1.selectAll().count())
        }

        transactionManager.execute {
            assertEquals(1, T1.selectAll().count())
        }
    }

    /**
     * Test for Propagation.NEVER
     * Execute non-transactionally, throw an exception if a transaction exists.
     */
    @RepeatedTest(5)
//    @Transactional(propagation = Propagation.NEVER) // see [runTestWithMockTransactional]
    open fun testPropagationNever() = runTestWithMockTransactional(
        propagationBehavior = TransactionDefinition.PROPAGATION_NEVER
    ) {
        assertFailsWith<IllegalStateException> { // Should Be "No transaction exists"
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.NEVER
     * Throw an exception cause outer transaction exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationNeverWithExistingTransaction() = runTestWithMockTransactional {
        assertFailsWith<IllegalTransactionStateException> {
            T1.insertRandom()
            transactionManager.execute(TransactionDefinition.PROPAGATION_NEVER) {
                T1.insertRandom()
            }
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Support a current transaction, throw an exception if none exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationMandatoryWithTransaction() = runTestWithMockTransactional {
        T1.insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Throw an exception cause no transaction exists.
     */
    @RepeatedTest(5)
    open fun testPropagationMandatoryWithoutTransaction() = runTest {
        assertFailsWith<IllegalTransactionStateException> {
            transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
                T1.insertRandom()
            }
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Support a current transaction, execute non-transactionally if none exists.
     */
    @RepeatedTest(5)
//    @Transactional // see [runTestWithMockTransactional]
    open fun testPropagationSupportWithTransaction() = runTestWithMockTransactional {
        T1.insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Execute non-transactionally if none exists.
     */
    @RepeatedTest(5)
    open fun testPropagationSupportWithoutTransaction() = runTest {
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            assertFailsWith<IllegalStateException> { // Should Be "No transaction exists"
                T1.insertRandom()
            }
        }
    }

    // TODO - Should be fixed by PR #2679
    @Disabled
    @RepeatedTest(5)
    //    @Transactional(isolation = Isolation.READ_COMMITTED) // see [runTestWithMockTransactional]
    open fun testIsolationLevelReadUncommitted() = runTestWithMockTransactional(
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    ) {
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
        T1.insertRandom()
        val count = T1.selectAll().count()
        transactionManager.execute(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW,
            TransactionDefinition.ISOLATION_READ_UNCOMMITTED
        ) {
            assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED)
            assertEquals(count, T1.selectAll().count())
        }
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
    }

    private suspend fun assertTransactionIsolationLevel(expected: Int) {
        val connection = TransactionManager.current().connection()
        assertEquals(expected.resolveIsolationLevel(), connection.getTransactionIsolation())
    }
}
