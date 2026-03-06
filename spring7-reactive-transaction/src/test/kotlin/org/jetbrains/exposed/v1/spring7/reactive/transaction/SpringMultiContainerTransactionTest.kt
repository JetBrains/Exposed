package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional

open class SpringMultiContainerTransactionTest {

    val orderContainer = AnnotationConfigApplicationContext(OrderConfig::class.java)
    val paymentContainer = AnnotationConfigApplicationContext(PaymentConfig::class.java)

    val orders: Orders = orderContainer.getBean(Orders::class.java)
    val payments: Payments = paymentContainer.getBean(Payments::class.java)

    @BeforeEach
    open fun beforeTest() = runTest {
        orders.init()
        payments.init()
    }

    @Test
    open fun test1() = runTest {
        Assertions.assertEquals(0, orders.findAll().size)
        Assertions.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test2() = runTest {
        orders.create()
        Assertions.assertEquals(1, orders.findAll().size)
        payments.create()
        Assertions.assertEquals(1, payments.findAll().size)
    }

    @Test
    open fun test3() = runTest {
        orders.suspendTransaction {
            payments.create()
            orders.create()
            payments.create()
        }
        Assertions.assertEquals(1, orders.findAll().size)
        Assertions.assertEquals(2, payments.findAll().size)
    }

    @Test
    open fun test4() = runTest {
        kotlin.runCatching {
            orders.suspendTransaction {
                orders.create()
                payments.create()
                throw SpringTransactionTestException()
            }
        }
        Assertions.assertEquals(0, orders.findAll().size)
        Assertions.assertEquals(1, payments.findAll().size)
    }

    @Test
    open fun test5() = runTest {
        kotlin.runCatching {
            orders.suspendTransaction {
                orders.create()
                payments.databaseTemplate {
                    payments.create()
                    throw SpringTransactionTestException()
                }
            }
        }
        Assertions.assertEquals(0, orders.findAll().size)
        Assertions.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test6() = runTest {
        Assertions.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assertions.assertEquals(0, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test7() = runTest {
        orders.createWithExposedTrxBlock()
        Assertions.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        payments.createWithExposedTrxBlock()
        Assertions.assertEquals(1, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test8() = runTest {
        orders.suspendTransaction {
            payments.createWithExposedTrxBlock()
            orders.createWithExposedTrxBlock()
            payments.createWithExposedTrxBlock()
        }
        Assertions.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        Assertions.assertEquals(2, payments.findAllWithExposedTrxBlock().size)
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class OrderConfig {

    @Bean
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///embeddedTest1;DB_CLOSE_DELAY=-1;")

    @Bean
    open fun transactionManager(connectionFactory: ConnectionFactory) = SpringReactiveTransactionManager(
        connectionFactory,
        R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
    )

    @Bean
    open fun orders() = Orders()
}

@Transactional
open class Orders {

    open suspend fun findAll(): List<ResultRow> = Order.selectAll().toList()

    // NOTE: qualifier names must be left in
    open suspend fun findAllWithExposedTrxBlock() = org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction { findAll() }

    open suspend fun create() = Order.insertAndGetId {
        it[buyer] = 123
    }.value

    // NOTE: qualifier names must be left in
    open suspend fun createWithExposedTrxBlock() = org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction { create() }

    open suspend fun init() {
        SchemaUtils.create(Order)
        Order.deleteAll()
    }

    open suspend fun suspendTransaction(block: suspend () -> Unit) {
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
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///embeddedTest2;DB_CLOSE_DELAY=-1;")

    @Bean
    open fun transactionManager(connectionFactory: ConnectionFactory) = SpringReactiveTransactionManager(
        connectionFactory,
        R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
    )

    @Bean
    open fun payments() = Payments()
}

@Transactional
open class Payments {

    open suspend fun findAll(): List<ResultRow> = Payment.selectAll().toList()

    open suspend fun findAllWithExposedTrxBlock() = suspendTransaction { findAll() }

    open suspend fun create() = Payment.insertAndGetId {
        it[state] = "state"
    }.value

    open suspend fun createWithExposedTrxBlock() = suspendTransaction { create() }

    open suspend fun init() {
        SchemaUtils.create(Payment)
        Payment.deleteAll()
    }

    open suspend fun databaseTemplate(block: suspend () -> Unit) {
        block()
    }
}

object Payment : LongIdTable("payments") {
    val state = varchar("state", 50)
}

private class SpringTransactionTestException : Error()
