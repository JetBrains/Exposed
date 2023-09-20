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

    @BeforeTest
    open fun beforeTest() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        TransactionTemplate(pm).apply {
            execute {
                SchemaUtils.create(T1)
            }
        }
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        if (pm !is SpringTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

        T1.insert {
            it[c1] = "112"
        }

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
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        val tt = TransactionTemplate(pm)

        transaction {
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }

            Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)

            tt.executeWithoutResult {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }

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
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }

            Assert.assertEquals(2, T1.selectAll().count())
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionCommit() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_NESTED

        tt.execute {
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }
            Assert.assertEquals(1, T1.selectAll().count())
            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
                Assert.assertEquals(2, T1.selectAll().count())
            }

            Assert.assertEquals(2, T1.selectAll().count())
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithNestedTransactionInnerRollback() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_NESTED

        tt.execute {
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }
            Assert.assertEquals(1, T1.selectAll().count())
            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
                Assert.assertEquals(2, T1.selectAll().count())
                it.setRollbackOnly()
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
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }
            Assert.assertEquals(T1.selectAll().count(), 1)
            it.setRollbackOnly()

            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
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
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

        tt.execute {
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }
            Assert.assertEquals(T1.selectAll().count(), 1)
            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
                Assert.assertEquals(T1.selectAll().count(), 1)
            }
            Assert.assertEquals(T1.selectAll().count(), 2)
        }

        tt.execute {
            Assert.assertEquals(T1.selectAll().count(), 2)
        }
    }

    @Test
    @Repeat(5)
    fun testConnectionWithRequiresNewWithInnerTransactionRollback() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

        tt.execute {
            T1.insert {
                it[c1] = Random().nextInt().toString()
            }
            Assert.assertEquals(T1.selectAll().count(), 1)
            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
                Assert.assertEquals(T1.selectAll().count(), 1)
                it.setRollbackOnly()
            }
            Assert.assertEquals(T1.selectAll().count(), 1)
        }

        tt.execute {
            Assert.assertEquals(T1.selectAll().count(), 1)
        }
    }

    @Test
    @Repeat(5)
    fun testPropagationNever() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_NEVER

        tt.execute {
            assertFailsWith<IllegalStateException> {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
            }
        }
    }

    @Test
    @Repeat(5)
    fun testPropagationNeverWithExistingTransaction() {
        val tm = ctx.getBean(PlatformTransactionManager::class.java)
        if (tm !is SpringTransactionManager) error("Wrong txManager instance: ${tm.javaClass.name}")
        val tt = TransactionTemplate(tm)

        tt.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

        assertFailsWith<IllegalTransactionStateException> {
            tt.execute {
                T1.insert {
                    it[c1] = Random().nextInt().toString()
                }
                tt.propagationBehavior = TransactionDefinition.PROPAGATION_NEVER
                tt.execute {
                    T1.insert {
                        it[c1] = Random().nextInt().toString()
                    }
                }
            }
        }
    }

    @AfterTest
    open fun afterTest() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        TransactionTemplate(pm).apply {
            execute {
                SchemaUtils.drop(T1)
            }
        }
    }
}
