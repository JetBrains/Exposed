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
    open fun beforeTest() {
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
        Assert.assertEquals(T1.selectAll().count(), 1)
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

    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionCommit() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(1, T1.selectAll().count())
            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) {
                T1.insertRandom()
                Assert.assertEquals(2, T1.selectAll().count())
            }
            Assert.assertEquals(2, T1.selectAll().count())
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionInnerRollback() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(1, T1.selectAll().count())
            transactionManager.execute(TransactionDefinition.PROPAGATION_NESTED) { status ->
                T1.insertRandom()
                Assert.assertEquals(2, T1.selectAll().count())
                status.setRollbackOnly()
            }
            Assert.assertEquals(1, T1.selectAll().count())
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionOuterRollback() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_NESTED

        tt.execute {
            T1.insertRandom()
            Assert.assertEquals(T1.selectAll().count(), 1)
            it.setRollbackOnly()

            tt.execute {
                T1.insertRandom()
                Assert.assertEquals(T1.selectAll().count(), 2)
            }
            Assert.assertEquals(T1.selectAll().count(), 2)
        }

        tt.execute {
            Assert.assertEquals(T1.selectAll().count(), 0)
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNew() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(T1.selectAll().count(), 1)
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                T1.insertRandom()
                Assert.assertEquals(T1.selectAll().count(), 1)
            }
            Assert.assertEquals(T1.selectAll().count(), 2)
        }

        transactionManager.execute {
            Assert.assertEquals(T1.selectAll().count(), 2)
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() {
        transactionManager.execute {
            T1.insertRandom()
            Assert.assertEquals(T1.selectAll().count(), 1)
            transactionManager.execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
                T1.insertRandom()
                Assert.assertEquals(T1.selectAll().count(), 1)
                it.setRollbackOnly()
            }
            Assert.assertEquals(T1.selectAll().count(), 1)
        }

        transactionManager.execute {
            Assert.assertEquals(T1.selectAll().count(), 1)
        }
    }

    @Test
    @Repeat(5)
    fun testPropagationNever() {
        transactionManager.execute(TransactionDefinition.PROPAGATION_NEVER) {
            assertFailsWith<IllegalStateException> {
                T1.insertRandom()
            }
        }
    }

    @Test
    @Repeat(5)
    fun testPropagationNeverWithExistingTransaction() {
        assertFailsWith<IllegalTransactionStateException> {
            transactionManager.execute {
                T1.insertRandom()
                transactionManager.execute(TransactionDefinition.PROPAGATION_NEVER) {
                    T1.insertRandom()
                }
            }
        }
    }

    @AfterTest
    open fun afterTest() {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }
}
