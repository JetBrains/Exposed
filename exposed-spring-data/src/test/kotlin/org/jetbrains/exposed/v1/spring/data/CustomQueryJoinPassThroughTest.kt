package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.mapLazy
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.spring.data.query.query
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

open class CustomQueryJoinPassThroughTest : ExposedSpringDataTestBase() {
    @Autowired
    override lateinit var repository: JoinPassThroughCustomQueryRepository

    @BeforeEach
    fun createOrderTable() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(CustomerOrders) }

    @AfterEach
    fun dropOrderTable() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(CustomerOrders) }

    @Test
    fun `maps joined customer and order columns inside the query block`() {
        val customers = seed()
        val annFirstOrderId = addOrder(customers[0], "tea", 4)
        val annSecondOrderId = addOrder(customers[0], "coffee", 7)
        val bobOrderId = addOrder(customers[1], "cake", 9)

        assertEquals(
            listOf(
                CustomerOrderLine(customers[0].id!!, annFirstOrderId, "Ann", "tea", 4),
                CustomerOrderLine(customers[0].id!!, annSecondOrderId, "Ann", "coffee", 7),
                CustomerOrderLine(customers[1].id!!, bobOrderId, "Bob", "cake", 9),
            ),
            repository.orderLines(),
        )
    }

    @Test
    fun `left join count includes customers without orders`() {
        val customers = seed()
        addOrder(customers[0], "tea", 4)
        addOrder(customers[1], "cake", 9)

        assertEquals(
            listOf(
                CustomerOrderCount("Ann", 1),
                CustomerOrderCount("Bob", 1),
                CustomerOrderCount("Carla", 0),
                CustomerOrderCount("Dan", 0),
            ),
            repository.orderCounts(),
        )
    }

    @Test
    fun `maps a single joined aggregate and returns null when the customer is absent`() {
        val customers = seed()
        addOrder(customers[2], "tea", 4)
        addOrder(customers[2], "coffee", 7)

        assertEquals(
            CustomerOrderView("Carla", "carla@example.com", 2, 11),
            repository.orderViewByEmail("carla@example.com"),
        )
        assertNull(repository.orderViewByEmailOrNull("nobody@example.com"))
    }

    @Test
    fun `filters joined results using an order column`() {
        val customers = seed()
        addOrder(customers[0], "tea", 4)
        addOrder(customers[0], "coffee", 7)
        addOrder(customers[1], "cake", 9)
        addOrder(customers[2], "cookie", 2)

        assertEquals(listOf("Ann", "Bob"), repository.customerNamesWithOrderOver(5))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `materializes lazy joined mapping inside the repository transaction`() {
        val customers = seed()
        val orderId = addOrder(customers[0], "tea", 4)

        val lines = repository.lazyOrderLines()

        assertEquals(listOf(CustomerOrderLine(customers[0].id!!, orderId, "Ann", "tea", 4)), lines.toList())
        assertEquals(lines.toList(), lines.toList())
    }

    private fun addOrder(customer: Customer, product: String, amount: Int): Long = checkNotNull(TransactionTemplate(tm).execute {
        CustomerOrders.insertAndGetId {
            it[CustomerOrders.customer] = EntityID(customer.id!!, Customers)
            it[CustomerOrders.product] = product
            it[CustomerOrders.amount] = amount
        }.value
    })
}

@ExposedRepository(table = Customers::class)
interface JoinPassThroughCustomQueryRepository : ExposedCrudRepository<Customer, Long> {
    fun orderLines(): List<CustomerOrderLine> = query {
        (Customers innerJoin CustomerOrders)
            .select(Customers.id, CustomerOrders.id, Customers.name, CustomerOrders.product, CustomerOrders.amount)
            .orderBy(Customers.name to SortOrder.ASC, CustomerOrders.id to SortOrder.ASC)
            .map {
                CustomerOrderLine(
                    it[Customers.id].value,
                    it[CustomerOrders.id].value,
                    it[Customers.name],
                    it[CustomerOrders.product],
                    it[CustomerOrders.amount],
                )
            }
    }

    fun orderCounts(): List<CustomerOrderCount> = query {
        val orderCount = CustomerOrders.id.count()
        (Customers leftJoin CustomerOrders)
            .select(Customers.name, orderCount)
            .groupBy(Customers.name)
            .orderBy(Customers.name)
            .map { CustomerOrderCount(it[Customers.name], it[orderCount]) }
    }

    fun orderViewByEmail(email: String): CustomerOrderView = query {
        val orderCount = CustomerOrders.id.count()
        val total = CustomerOrders.amount.sum()
        (Customers leftJoin CustomerOrders)
            .select(Customers.name, Customers.email, orderCount, total)
            .where { Customers.email eq email }
            .groupBy(Customers.name, Customers.email)
            .single()
            .let { CustomerOrderView(it[Customers.name], it[Customers.email], it[orderCount], it[total]) }
    }

    fun orderViewByEmailOrNull(email: String): CustomerOrderView? = query {
        val orderCount = CustomerOrders.id.count()
        val total = CustomerOrders.amount.sum()
        (Customers leftJoin CustomerOrders)
            .select(Customers.name, Customers.email, orderCount, total)
            .where { Customers.email eq email }
            .groupBy(Customers.name, Customers.email)
            .singleOrNull()
            ?.let { CustomerOrderView(it[Customers.name], it[Customers.email], it[orderCount], it[total]) }
    }

    fun customerNamesWithOrderOver(amount: Int): List<String> = query {
        (Customers innerJoin CustomerOrders)
            .select(Customers.name)
            .where { CustomerOrders.amount greater amount }
            .map { it[Customers.name] }
            .distinct()
            .sorted()
    }

    fun lazyOrderLines(): Iterable<CustomerOrderLine> = query {
        (Customers innerJoin CustomerOrders)
            .select(Customers.id, CustomerOrders.id, Customers.name, CustomerOrders.product, CustomerOrders.amount)
            .orderBy(Customers.name)
            .mapLazy {
                CustomerOrderLine(
                    it[Customers.id].value,
                    it[CustomerOrders.id].value,
                    it[Customers.name],
                    it[CustomerOrders.product],
                    it[CustomerOrders.amount],
                )
            }
    }
}

object CustomerOrders : LongIdTable("customer_orders") {
    val customer = reference("customer_id", Customers)
    val product = varchar("product", 255)
    val amount = integer("amount")
}

data class CustomerOrderLine(val customerId: Long, val orderId: Long, val customerName: String, val product: String, val amount: Int)

data class CustomerOrderCount(val name: String, val orders: Long)

data class CustomerOrderView(val name: String, val email: String, val orderCount: Long, val total: Int?)