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
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.assertNull

open class ExposedTransactionManagerTest : SpringTransactionTestBase() {

    object t1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    @Test @Transactional @Commit
    @Repeat(5)
    open fun testConnection() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        if(pm !is SpringTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

        SchemaUtils.create(t1)
        t1.insert {
            it[t1.c1] = "112"
        }

        Assert.assertEquals(t1.selectAll().count(), 1)
        SchemaUtils.drop(t1)
    }

    @Test @Transactional @Commit
    @Repeat(5)
    open fun testConnection2() {
        SchemaUtils.create(t1)
        val rnd = Random().nextInt().toString()
        t1.insert {
            it[t1.c1] = rnd
        }

        Assert.assertEquals(t1.selectAll().single()[t1.c1], rnd)
        SchemaUtils.drop(t1)
    }

    @Test
    @Repeat(5)
    fun testConnectionWithoutAnnotation() {
        transaction {
            SchemaUtils.create(t1)
            val rnd = Random().nextInt().toString()
            t1.insert {
                it[t1.c1] = rnd
            }

            Assert.assertEquals(t1.selectAll().single()[t1.c1], rnd)
        }
        assertNull(TransactionManager.currentOrNull())
        transaction {
            val rnd = Random().nextInt().toString()
            t1.insert {
                it[t1.c1] = rnd
            }

            Assert.assertEquals(2, t1.selectAll().count())
            SchemaUtils.drop(t1)
        }
    }
}