package org.jetbrains.exposed.v1.spring

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assert
import org.junit.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import kotlin.test.BeforeTest

open class SpringMultiContainerTransactionTest {

    val orderContainer = AnnotationConfigApplicationContext(OrderConfig::class.java)
    val paymentContainer = AnnotationConfigApplicationContext(PaymentConfig::class.java)

    val orders: Orders = orderContainer.getBean(Orders::class.java)
    val payments: Payments = paymentContainer.getBean(Payments::class.java)

    @BeforeTest
    open fun beforeTest() {
        orders.init()
        payments.init()
    }

    @Test
    open fun test1() {
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test2() {
        orders.create()
        Assert.assertEquals(1, orders.findAll().size)
        payments.create()
        Assert.assertEquals(1, payments.findAll().size)
    }

    @Test
    open fun test3() {
        orders.transaction {
            payments.create()
            orders.create()
            payments.create()
        }
        Assert.assertEquals(1, orders.findAll().size)
        Assert.assertEquals(2, payments.findAll().size)
    }

    @Test
    open fun test4() {
        kotlin.runCatching {
            orders.transaction {
                orders.create()
                payments.create()
                throw SpringTransactionTestException()
            }
        }
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(1, payments.findAll().size)
    }

    @Test
    open fun test5() {
        kotlin.runCatching {
            orders.transaction {
                orders.create()
                payments.databaseTemplate {
                    payments.create()
                    throw SpringTransactionTestException()
                }
            }
        }
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test6() {
        Assert.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(0, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test7() {
        orders.createWithExposedTrxBlock()
        Assert.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        payments.createWithExposedTrxBlock()
        Assert.assertEquals(1, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test8() {
        orders.transaction {
            payments.createWithExposedTrxBlock()
            orders.createWithExposedTrxBlock()
            payments.createWithExposedTrxBlock()
        }
        Assert.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(2, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test9() {
        kotlin.runCatching {
            orders.transaction {
                orders.createWithExposedTrxBlock()
                payments.createWithExposedTrxBlock()
                throw SpringTransactionTestException()
            }
        }
        Assert.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(1, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test10() {
        kotlin.runCatching {
            orders.transaction {
                orders.createWithExposedTrxBlock()
                payments.databaseTemplate {
                    payments.createWithExposedTrxBlock()
                    throw SpringTransactionTestException()
                }
            }
        }
        Assert.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(0, payments.findAllWithExposedTrxBlock().size)
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class OrderConfig {

    @Bean
    open fun dataSource(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName("embeddedTest1").setType(
        EmbeddedDatabaseType.H2
    ).build()

    @Bean
    open fun transactionManager(dataSource: DataSource) = SpringTransactionManager(dataSource)

    @Bean
    open fun orders() = Orders()
}

@Transactional
open class Orders {

    open fun findAll(): List<ResultRow> = Order.selectAll().toList()

    open fun findAllWithExposedTrxBlock() = org.jetbrains.exposed.v1.jdbc.transactions.transaction { findAll() }

    open fun create() = Order.insertAndGetId {
        it[buyer] = 123
    }.value

    open fun createWithExposedTrxBlock() = org.jetbrains.exposed.v1.jdbc.transactions.transaction { create() }

    open fun init() {
        SchemaUtils.create(Order)
        Order.deleteAll()
    }

    open fun transaction(block: () -> Unit) {
        block()
    }
}

object Order : LongIdTable("orders") {
    val buyer = long("buyer_id")
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class PaymentConfig {

    @Bean
    open fun dataSource(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName("embeddedTest2").setType(
        EmbeddedDatabaseType.H2
    ).build()

    @Bean
    open fun transactionManager(dataSource: DataSource) = SpringTransactionManager(dataSource)

    @Bean
    open fun payments() = Payments()
}

@Transactional
open class Payments {

    open fun findAll(): List<ResultRow> = Payment.selectAll().toList()

    open fun findAllWithExposedTrxBlock() = transaction { findAll() }

    open fun create() = Payment.insertAndGetId {
        it[state] = "state"
    }.value

    open fun createWithExposedTrxBlock() = transaction { create() }

    open fun init() {
        SchemaUtils.create(Payment)
        Payment.deleteAll()
    }

    open fun databaseTemplate(block: () -> Unit) {
        block()
    }
}

object Payment : LongIdTable("payments") {
    val state = varchar("state", 50)
}

private class SpringTransactionTestException : Error()
