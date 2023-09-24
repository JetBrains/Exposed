package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.test.annotation.Repeat
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFailsWith

open class ExposedTransactionManagerTest : SpringTransactionTestBase() {

    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    private fun T1.insertRandom() {
        insert {
            it[c1] = Random().nextInt().toString()
        }
    }

    private fun PlatformTransactionManager.execute(
        propagationBehavior: Int = TransactionDefinition.PROPAGATION_REQUIRED,
        block: (TransactionStatus) -> Unit
    ) {
        if (this !is SpringTransactionManager) error("Wrong txManager instance: ${this.javaClass.name}")
        val tt = TransactionTemplate(this)
        tt.propagationBehavior = propagationBehavior
        tt.executeWithoutResult {
            block(it)
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
        Assert.assertEquals(1, T1.selectAll().count())
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
        Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)
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
            Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)

            transactionManager.execute {
                T1.insertRandom()
                Assert.assertEquals(2, T1.selectAll().count())
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
        Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)

        transaction {
            T1.insertRandom()
            Assert.assertEquals(2, T1.selectAll().count())
        }
    }

    /**
     * Test For Propagation.NESTED
     * Execute within a nested transaction if a current transaction exists, behave like REQUIRED otherwise.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionCommit() {
        T1.insertRandom()
        Assert.assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
            T1.insertRandom()
            Assert.assertEquals(2, T1.selectAll().count())
        }
        Assert.assertEquals(2, T1.selectAll().count())
    }

    /**
     * Test For Propagation.NESTED with inner roll-back
     * The nested transaction will be roll-back only inner transaction when the transaction marks as rollback.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithNestedTransactionInnerRollback() {
        T1.insertRandom()
        Assert.assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) { status ->
            T1.insertRandom()
            Assert.assertEquals(2, T1.selectAll().count())
            status.setRollbackOnly()
        }
        Assert.assertEquals(1, T1.selectAll().count())
    }

    /**
     * Test For Propagation.NESTED with outer roll-back
     * The nested transaction will be roll-back entire transaction when the transaction marks as rollback.
     */
    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionOuterRollback() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(1, T1.selectAll().count())
            it.setRollbackOnly()

            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                T1.insertRandom()
                Assert.assertEquals(2, T1.selectAll().count())
            }
            Assert.assertEquals(2, T1.selectAll().count())
        }

        transactionManager.execute {
            Assert.assertEquals(0, T1.selectAll().count())
        }
    }

    /**
     * Test For Propagation.REQUIRED
     * Create a new transaction, and suspend the current transaction if one exists.
     */
    @Test
    @Repeat(5)
    @Transactional
    open fun testConnectionWithRequiresNew() {
        T1.insertRandom()
        Assert.assertEquals(1, T1.selectAll().count())
        transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            Assert.assertEquals(0, T1.selectAll().count())
            T1.insertRandom()
            Assert.assertEquals(1, T1.selectAll().count())
        }
        Assert.assertEquals(2, T1.selectAll().count())
    }

    /**
     * Test For Propagation.REQUIRED with inner transaction roll-back
     * The inner transaction will be roll-back only inner transaction when the transaction marks as rollback.
     * And since isolation level is READ_COMMITTED, the inner transaction can't see the changes of outer transaction.
     */
    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(1, T1.selectAll().count())
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                T1.insertRandom()
                Assert.assertEquals(1, T1.selectAll().count())
                it.setRollbackOnly()
            }
            Assert.assertEquals(1, T1.selectAll().count())
        }

        transactionManager.execute {
            Assert.assertEquals(1, T1.selectAll().count())
        }
    }

    /**
     * Test For Propagation.NEVER
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
     * Test For Propagation.NEVER
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
     * Test For Propagation.MANDATORY
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
     * Test For Propagation.MANDATORY
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
     * Test For Propagation.SUPPORTS
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
     * Test For Propagation.SUPPORTS
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

    @AfterTest
    fun afterTest() {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }
}
