package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.viewThreadStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.test.annotation.Commit
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import java.util.*
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

    @BeforeEach
    fun beforeTest() = runTest {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
    }

    @AfterEach
    fun afterTest() = runTest {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }

    @RepeatedTest(5)
    //    @Transactional // see [runTestWithMockTransactional]
    @Commit
    open fun testConnection() = runTestWithMockTransactional {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
    }

    @RepeatedTest(5)
    //    @Transactional // see [runTestWithMockTransactional]
    @Commit
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

    // TODO - This (& only this test) fails because of line 114 in suspendTransaction();
    // If the line is reverted to original, it passes -> ThreadLocalTransactionsStack.getTransactionOrNull(databaseToUse)
    @RepeatedTest(1)
    @Commit
//    @Transactional // see [runTestWithMockTransactional]
    open fun testConnectionCombineWithExposedTransaction2() = runTestWithMockTransactional {
        println("Starting TEST...\n${viewThreadStack()}")
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }
        assertEquals(rnd, T1.selectAll().single()[T1.c1])

        println("About to enter nested...")
        suspendTransaction {
            println("Starting NESTED...\n${viewThreadStack()}")
            T1.insertRandom()
            assertEquals(2, T1.selectAll().count())
            println("NESTED = ${T1.selectAll().count()}")
            println("Finishing NESTED...")
        }
        println("TEST = ${T1.selectAll().count()}")
        println("Finishing TEST...\n${viewThreadStack()}")
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

    /**
     * Test for Propagation.REQUIRES_NEW
     * Create a new transaction, and suspend the current transaction if one exists.
     */
    @RepeatedTest(5)
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

    /**
     * Test for Propagation.REQUIRES_NEW with inner transaction roll-back
     * The inner transaction will be roll-back only inner transaction when the transaction marks as rollback.
     * And since isolation level is READ_COMMITTED, the inner transaction can't see the changes of outer transaction.
     */
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
}
