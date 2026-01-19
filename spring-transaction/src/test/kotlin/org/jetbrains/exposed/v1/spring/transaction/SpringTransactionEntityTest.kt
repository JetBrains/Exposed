package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.util.UUID as JavaUUID

object CustomerTable : UUIDTable(name = "customer") {
    val name = varchar(name = "name", length = 255).uniqueIndex()
}

class CustomerDAO(id: EntityID<JavaUUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CustomerDAO>(CustomerTable)

    var name by CustomerTable.name
}

object OrderTable : UUIDTable(name = "orders") {
    val customer = reference(name = "customer_id", foreign = CustomerTable)
    val product = varchar(name = "product", length = 255)
}

class OrderDAO(id: EntityID<JavaUUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<OrderDAO>(OrderTable)

    var customer by CustomerDAO.referencedOn(OrderTable.customer)
    var product by OrderTable.product
}

@org.springframework.stereotype.Service
@Transactional
open class Service {

    open fun init() {
        SchemaUtils.create(CustomerTable, OrderTable)
    }

    open fun createCustomer(name: String): CustomerDAO {
        return CustomerDAO.new {
            this.name = name
        }
    }

    open fun createOrder(customer: CustomerDAO, product: String): OrderDAO {
        return OrderDAO.new {
            this.customer = customer
            this.product = product
        }
    }

    open fun doBoth(name: String, product: String): OrderDAO {
        return createOrder(createCustomer(name), product)
    }

    open fun findOrderByProduct(product: String): OrderDAO? {
        return OrderDAO.find { OrderTable.product eq product }.singleOrNull()
    }

    open fun transaction(block: () -> Unit) {
        block()
    }

    open fun cleanUp() {
        SchemaUtils.drop(CustomerTable, OrderTable)
    }
}

open class SpringTransactionEntityTest : SpringTransactionTestBase() {

    @Autowired
    lateinit var service: Service

    @BeforeEach
    open fun beforeTest() {
        service.init()
    }

    @Test
    @Commit
    open fun test01() {
        val customer = service.createCustomer("Alice1")
        service.createOrder(customer, "Product1")
        val order = service.findOrderByProduct("Product1")
        assertNotNull(order)
        service.transaction {
            assertEquals("Alice1", order.customer.name)
        }
    }

    @Test
    @Commit
    fun test02() {
        service.doBoth("Bob", "Product2")
        val order = service.findOrderByProduct("Product2")
        assertNotNull(order)
        service.transaction {
            assertEquals("Bob", order.customer.name)
        }
    }

    @AfterEach
    fun afterTest() {
        service.cleanUp()
    }
}
