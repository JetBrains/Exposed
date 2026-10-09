@file:OptIn(ExperimentalR2dbcDaoApi::class)

package org.jetbrains.exposed.v1.spring7.reactive.transaction

import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.dao.r2dbc.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.r2dbc.java.UUIDEntityClass
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

object CustomerTable : UUIDTable(name = "customer") {
    val name = varchar(name = "name", length = 255).uniqueIndex()
}

class CustomerDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CustomerDAO>(CustomerTable)

    var name by CustomerTable.name
}

object OrderTable : UUIDTable(name = "orders") {
    val customer = reference(name = "customer_id", foreign = CustomerTable)
    val product = varchar(name = "product", length = 255)
}

class OrderDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OrderDAO>(OrderTable)

    val customer by CustomerDAO.referencedOn(OrderTable.customer)
    var product by OrderTable.product
}

@org.springframework.stereotype.Service
@Transactional
open class Service {

    open suspend fun init() {
        SchemaUtils.create(CustomerTable, OrderTable)
    }

    open suspend fun createCustomer(name: String): CustomerDAO {
        return CustomerDAO.new {
            this.name = name
        }
    }

    open suspend fun createOrder(customer: CustomerDAO, product: String): OrderDAO {
        return OrderDAO.new {
            this.customer.set(customer)
            this.product = product
        }
    }

    open suspend fun doBoth(name: String, product: String): OrderDAO {
        return createOrder(createCustomer(name), product)
    }

    open suspend fun findOrderByProduct(product: String): OrderDAO? {
        return OrderDAO.find { OrderTable.product eq product }.singleOrNull()
    }

    open suspend fun suspendTransaction(block: suspend () -> Unit) {
        block()
    }

    open suspend fun cleanUp() {
        SchemaUtils.drop(CustomerTable, OrderTable)
    }
}

open class SpringTransactionEntityTest : SpringReactiveTransactionTestBase() {
    @Autowired
    lateinit var service: Service

    @BeforeEach
    open fun beforeTest() = runTest {
        service.init()
    }

    @AfterEach
    fun afterTest() = runTest {
        service.cleanUp()
    }

    @Test
    @Commit
    open fun test01() = runTest {
        val customer = service.createCustomer("Alice1")
        service.createOrder(customer, "Product1")
        val order = service.findOrderByProduct("Product1")
        assertNotNull(order)
        service.suspendTransaction {
            assertEquals("Alice1", order.customer().name)
        }
    }

    @Test
    @Commit
    fun test02() = runTest {
        service.doBoth("Bob", "Product2")
        val order = service.findOrderByProduct("Product2")
        assertNotNull(order)
        service.suspendTransaction {
            assertEquals("Bob", order.customer().name)
        }
    }
}
