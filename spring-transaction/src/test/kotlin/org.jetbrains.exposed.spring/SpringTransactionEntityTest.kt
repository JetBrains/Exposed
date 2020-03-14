package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.Commit
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


private object CustomerTable: UUIDTable(name = "customer") {
    val name = varchar(name = "name", length = 255).uniqueIndex()
}

class CustomerDAO(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<CustomerDAO>(CustomerTable)

    var name by CustomerTable.name
}

object OrderTable: UUIDTable(name = "orders") {
    val customer = reference(name = "customer_id", foreign = CustomerTable)
    val product = varchar(name = "product", length = 255)
}

class OrderDAO(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<OrderDAO>(OrderTable)

    var customer by CustomerDAO.referencedOn(OrderTable.customer)
    var product by OrderTable.product
}

@org.springframework.stereotype.Service
@Transactional
open class Service {
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

    open fun findOrderByProduct(product: String) : OrderDAO? {
        return OrderDAO.find { OrderTable.product eq product }.singleOrNull()
    }
}

open class SpringTransactionEntityTest : SpringTransactionTestBase() {

    @Autowired
    lateinit var service: Service

    @Test @Commit
    open fun test01() {
        transaction {
            SchemaUtils.create(CustomerTable, OrderTable)
        }

        val customer = service.createCustomer("Alice1")
        service.createOrder(customer, "Product1")
        val order = service.findOrderByProduct("Product1")
        assertNotNull(order)
        transaction {
            assertEquals("Alice1", order.customer.name)
        }
    }

    @Test @Commit
    fun test02(){
        service.doBoth("Bob", "Product2")
        val order = service.findOrderByProduct("Product2")
        assertNotNull(order)
        transaction {
        assertEquals("Bob", order.customer.name)
            SchemaUtils.drop(CustomerTable, OrderTable)
        }
    }
}