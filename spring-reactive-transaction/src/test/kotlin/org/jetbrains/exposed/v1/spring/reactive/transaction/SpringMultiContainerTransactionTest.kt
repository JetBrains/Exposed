package org.jetbrains.exposed.v1.spring.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

// TODO - Confirm whether Spring R2DBC supports Distributed/XA transactions
// Interacting with multiple databases within a single transactional scope seems to be unsupported so far.
// But we need to assess if that's a limitation that could be overcome by our SRxTM or if any other managers even support it.
open class SpringMultiContainerTransactionTest {

    val orderContainer = AnnotationConfigApplicationContext(OrderConfig::class.java)
    val paymentContainer = AnnotationConfigApplicationContext(PaymentConfig::class.java)

    val orders: Orders = orderContainer.getBean(Orders::class.java)
    val payments: Payments = paymentContainer.getBean(Payments::class.java)

    @OptIn(InternalApi::class)
    @BeforeTest
    open fun beforeTest() = runTest {
        orders.init()
        payments.init()
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(InternalApi::class)
    @AfterTest
    open fun afterTest() {
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @Test
    open fun test1() = runTest {
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test2() = runTest {
        orders.create()
        Assert.assertEquals(1, orders.findAll().size)
        payments.create()
        Assert.assertEquals(1, payments.findAll().size)
    }

    @Ignore
    @Test
    open fun test3() = runTest {
        orders.suspendTransaction {
            payments.create()
            orders.create()
            payments.create()
        }
        Assert.assertEquals(1, orders.findAll().size)
        Assert.assertEquals(2, payments.findAll().size)
    }

    @Ignore
    @Test
    open fun test4() = runTest {
        kotlin.runCatching {
            orders.suspendTransaction {
                orders.create()
                payments.create()
                throw SpringTransactionTestException()
            }
        }
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(1, payments.findAll().size)
    }

    @Ignore
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
        Assert.assertEquals(0, orders.findAll().size)
        Assert.assertEquals(0, payments.findAll().size)
    }

    @Test
    open fun test6() = runTest {
        Assert.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(0, payments.findAllWithExposedTrxBlock().size)
    }

    @Test
    open fun test7() = runTest {
        orders.createWithExposedTrxBlock()
        Assert.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        payments.createWithExposedTrxBlock()
        Assert.assertEquals(1, payments.findAllWithExposedTrxBlock().size)
    }

    @Ignore
    @Test
    open fun test8() = runTest {
        orders.suspendTransaction {
            payments.createWithExposedTrxBlock()
            orders.createWithExposedTrxBlock()
            payments.createWithExposedTrxBlock()
        }
        Assert.assertEquals(1, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(2, payments.findAllWithExposedTrxBlock().size)
    }

    @Ignore
    @Test
    open fun test9() = runTest {
        kotlin.runCatching {
            orders.suspendTransaction {
                orders.createWithExposedTrxBlock()
                payments.createWithExposedTrxBlock()
                throw SpringTransactionTestException()
            }
        }
        Assert.assertEquals(0, orders.findAllWithExposedTrxBlock().size)
        Assert.assertEquals(1, payments.findAllWithExposedTrxBlock().size)
    }

    @Ignore
    @Test
    open fun test10() = runTest {
        kotlin.runCatching {
            orders.suspendTransaction {
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
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///testDb1;DB_CLOSE_DELAY=-1;")

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

    open suspend fun findAllWithExposedTrxBlock() = org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction { findAll() }

    open suspend fun create() = Order.insertAndGetId {
        it[buyer] = 123
    }.value

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
    open fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///testDb2;DB_CLOSE_DELAY=-1;")

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
