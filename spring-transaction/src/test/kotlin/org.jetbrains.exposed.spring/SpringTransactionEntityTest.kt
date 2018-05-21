package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.UUIDTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.springframework.transaction.annotation.Transactional
import java.util.*


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

    var customer by OrderTable.customer
    var product by OrderTable.product
}

@Transactional
open class Service {
    open fun createCustomer(name: String): CustomerDAO {
        return CustomerDAO.new {
            this.name = name
        }
    }

    open fun createOrder(customer: CustomerDAO, product: String): OrderDAO {
        return OrderDAO.new {
            this.customer = customer.id
            this.product = product
        }
    }

    open fun doBoth(name: String, product: String): OrderDAO {
        return createOrder(createCustomer(name), product)
    }
}

open class SpringTransactionEntityTest : SpringTransactionTestBase() {

    val service: Service = Service()

    @Test
    fun test01(){
        transaction {
            SchemaUtils.create(CustomerTable, OrderTable)
        }

        val customer = service.createCustomer("Alice1")
        service.createOrder(customer, "SpringProduct")
    }

    @Test
    fun test02(){
        service.doBoth("Bob", "SpringProduct")
        transaction {
            SchemaUtils.drop(CustomerTable, OrderTable)
        }
    }
}