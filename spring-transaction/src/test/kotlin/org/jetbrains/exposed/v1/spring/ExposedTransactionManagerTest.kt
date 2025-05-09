package org.jetbrains.exposed.v1.spring

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.insert
import org.jetbrains.exposed.v1.sql.selectAll
import org.jetbrains.exposed.v1.sql.transactions.TransactionManager
import org.jetbrains.exposed.v1.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.test.annotation.Repeat
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLTimeoutException
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

open class ExposedTransactionManagerTest : SpringTransactionTestBase() {

    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    private fun T1.insertRandom() {
        insert {
            it[c1] = Random().nextInt().toString()
        }
    }

    @BeforeTest
    fun beforeTest() {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection() {
        T1.insertRandom()
        assertEquals(1, T1.selectAll().count())
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection2() {
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }
        assertEquals(rnd, T1.selectAll().single()[T1.c1])
    }

    @Test
    @Repeat(5)
    @Commit
    open fun testConnectionCombineWithExposedTransaction() {
        transaction {
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }
            assertEquals(rnd, T1.selectAll().single()[T1.c1])

            transactionManager.execute {
                T1.insertRandom()
                assertEquals(2, T1.selectAll().count())
            }
        }
    }

    @Test
    @Repeat(5)
    @Commit
    @Transactional
    open fun testConnectionCombineWithExposedTransaction2() {
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }
        assertEquals(rnd, T1.selectAll().single()[T1.c1])

        transaction {
            T1.insertRandom()
            assertEquals(2, T1.selectAll().count())
        }
    }

    /**
     * Test for Propagation.NESTED
     * Execute within a nested transaction if a current transaction exists, behave like REQUIRED otherwise.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionCommit() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionInnerRollback() {
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
    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionOuterRollback() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithRequiresNew() {
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
    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() {
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
    @Test
    @Repeat(5)
    @Transactional(propagation = Propagation.NEVER)
    open fun testPropagationNever() {
        assertFailsWith<IllegalStateException> { // Should Be "No transaction exist"
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.NEVER
     * Throw an exception cause outer transaction exists.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationNeverWithExistingTransaction() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationMandatoryWithTransaction() {
        T1.insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_MANDATORY) {
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.MANDATORY
     * Throw an exception cause no transaction exists.
     */
    @Test
    @Repeat(5)
    open fun testPropagationMandatoryWithoutTransaction() {
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
    @Test
    @Repeat(5)
    @Transactional
    open fun testPropagationSupportWithTransaction() {
        T1.insertRandom()
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            T1.insertRandom()
        }
    }

    /**
     * Test for Propagation.SUPPORTS
     * Execute non-transactionally if none exists.
     */
    @Test
    @Repeat(5)
    open fun testPropagationSupportWithoutTransaction() {
        transactionManager.execute(TransactionDefinition.PROPAGATION_SUPPORTS) {
            assertFailsWith<IllegalStateException> { // Should Be "No transaction exist"
                T1.insertRandom()
            }
        }
    }

    /**
     * Test for Isolation Level
     */
    @Test
    @Repeat(5)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    open fun testIsolationLevelReadUncommitted() {
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
        T1.insertRandom()
        val count = T1.selectAll().count()
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, TransactionDefinition.ISOLATION_READ_UNCOMMITTED) {
            assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED)
            assertEquals(count, T1.selectAll().count())
        }
        assertTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED)
    }

    /**
     * Test for Timeout
     * Execute with query timeout
     */
    @Test
    @Repeat(5)
    open fun testTimeout() {
        transactionManager.execute(timeout = 1) {
            try {
                // H2 database doesn't support sql sleep function so use recursive query to simulate long running query
                TransactionManager.current().exec(
                    """
               WITH RECURSIVE T(N) AS (
               SELECT 1
               UNION ALL
               SELECT N+1 FROM T WHERE N<10000000
               )
               SELECT * FROM T;
                    """.trimIndent(),
                    explicitStatementType = StatementType.SELECT
                )
                fail("Should have thrown a timeout exception")
            } catch (cause: ExposedSQLException) {
                assertTrue(cause.cause is SQLTimeoutException)
            }
        }
    }

    @AfterTest
    fun afterTest() {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }

    private fun assertTransactionIsolationLevel(expected: Int) {
        val connection = TransactionManager.current().connection
        assertEquals(expected, connection.transactionIsolation)
    }
}
