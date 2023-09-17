package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.Test
import org.springframework.test.annotation.Commit
import org.springframework.test.annotation.Repeat
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import kotlin.test.assertNull

open class ExposedTransactionManagerTest : SpringTransactionTestBase() {

    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        if (pm !is SpringTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

        SchemaUtils.create(T1)
        T1.insert {
            it[c1] = "112"
        }

        Assert.assertEquals(T1.selectAll().count(), 1)
        SchemaUtils.drop(T1)
    }

    @Test
    @Transactional
    @Commit
    @Repeat(5)
    open fun testConnection2() {
        SchemaUtils.create(T1)
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }

        Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)
        SchemaUtils.drop(T1)
    }

    @Test
    @Repeat(5)
    @Commit
    open fun testConnectionCombineWithExposedTransaction() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        val tt = TransactionTemplate(pm)

        transaction {
            SchemaUtils.create(T1)
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }

            Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)

            tt.executeWithoutResult {
                val rnd = Random().nextInt().toString()
                T1.insert {
                    it[c1] = rnd
                }

                Assert.assertEquals(2, T1.selectAll().count())
            }
            SchemaUtils.drop(T1)
        }
    }

    @Test
    @Repeat(5)
    @Commit
    @Transactional
    open fun testConnectionCombineWithExposedTransaction2() {
        SchemaUtils.create(T1)
        val rnd = Random().nextInt().toString()
        T1.insert {
            it[c1] = rnd
        }

        Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)

        transaction {
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }

            Assert.assertEquals(2, T1.selectAll().count())
        }
        SchemaUtils.drop(T1)
    }

    @Test
    @Repeat(5)
    fun testConnectionWithoutAnnotation() {
        transaction {
            SchemaUtils.create(T1)
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }

            Assert.assertEquals(T1.selectAll().single()[T1.c1], rnd)
        }
        assertNull(TransactionManager.currentOrNull())
        transaction {
            val rnd = Random().nextInt().toString()
            T1.insert {
                it[c1] = rnd
            }

            Assert.assertEquals(2, T1.selectAll().count())
            SchemaUtils.drop(T1)
        }
    }

    @Test
    @Transactional(propagation = Propagation.NESTED)
    open fun testConnectionWithNestedTransaction() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        if (pm !is SpringTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

        SchemaUtils.create(T1)
        T1.insert {
            it[c1] = "112"
        }

        Assert.assertEquals(T1.selectAll().count(), 1)
        SchemaUtils.drop(T1)
    }
}
