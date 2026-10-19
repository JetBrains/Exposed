package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.transaction.support.TransactionTemplate

/**
 * Derived queries that filter by properties of other tables: to-many hops declared with [ExposedReferencedBy],
 * to-one hops through foreign key columns, and paths over several tables (also tables without an id column).
 */
open class DerivedQueryJoinTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: JoinCustomerRepository

    @Autowired
    lateinit var orders: OrderRepository

    @BeforeEach
    fun createOrderTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Products, Orders, OrderItems, OrderComments) }

    @AfterEach
    fun dropOrderTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(OrderComments, OrderItems, Orders, Products) }

    private lateinit var first: Order
    private lateinit var second: Order
    private lateinit var third: Order

    /**
     * - first (note "a"): two "Pen" items (Ink), one "Pad" item (Paper), comment "fast"
     * - second (note "b"): one "pencil" item (Wood), comment "slow", which replies to first
     * - third (note "a"): no items, no comments
     */
    private fun seedOrders() {
        val (ann, bob) = seed()
        val ink = Products.insertAndGetId { it[name] = "Ink" }
        val paper = Products.insertAndGetId { it[name] = "Paper" }
        val wood = Products.insertAndGetId { it[name] = "Wood" }
        val bobId = checkNotNull(bob.id)
        first = orders.save(Order(customer = checkNotNull(ann.id), note = "a"))
        second = orders.save(Order(customer = bobId, note = "b"))
        third = orders.save(Order(customer = bobId, note = "a"))
        item(first, "Pen", 1, ink)
        item(first, "Pen", 2, ink)
        item(first, "Pad", 5, paper)
        item(second, "pencil", 3, wood)
        OrderComments.insert {
            it[order] = first.id!!
            it[text] = "fast"
        }
        OrderComments.insert {
            it[order] = second.id!!
            it[replyTo] = first.id!!
            it[text] = "slow"
        }
    }

    private fun item(order: Order, name: String, quantity: Int, product: EntityID<Long>) {
        OrderItems.insert {
            it[OrderItems.order] = order.id!!
            it[OrderItems.product] = product
            it[OrderItems.name] = name
            it[OrderItems.quantity] = quantity
        }
    }

    private fun Iterable<Order>.ids() = map { it.id!! }.sorted()

    // ---- to-many ----

    @Test
    fun testFindByToManyProperty() {
        seedOrders()
        assertEquals(listOf(first.id), orders.findByItemsName("Pad").ids())
        assertEquals(listOf(second.id), orders.findByItemsName("pencil").ids())
        assertTrue(orders.findByItemsName("Nothing").isEmpty())
    }

    @Test
    fun testOrderWithSeveralMatchingChildrenIsReturnedOnce() {
        seedOrders()
        assertEquals(listOf(first), orders.findByItemsName("Pen"))
    }

    @Test
    fun testNestedPathIsQueriedWithSubqueries() {
        seedOrders()
        val sql = captureSelects { orders.findByItemsProductName("Ink") }.single().uppercase()
        assertFalse("JOIN" in sql) { sql }
        assertEquals(2, Regex("IN \\(SELECT").findAll(sql).count()) { sql }
    }

    @Test
    fun testToManyCombinedWithRootProperty() {
        seedOrders()
        assertEquals(listOf(first.id), orders.findByItemsNameAndNote("Pen", "a").ids())
        assertTrue(orders.findByItemsNameAndNote("Pen", "b").isEmpty())
        assertEquals(listOf(first.id, second.id), orders.findByItemsNameOrNote("Pen", "b").ids())
    }

    @Test
    fun testToManyWithComparisonAndIgnoreCase() {
        seedOrders()
        assertEquals(listOf(first.id), orders.findByItemsQuantityGreaterThan(4).ids())
        assertEquals(listOf(first.id, second.id), orders.findByItemsNameContainingIgnoreCase("PEN").ids())
        assertEquals(listOf(first.id), orders.findByItemsNameIgnoreCase("PEN").ids())
        assertEquals(listOf(second.id), orders.findByItemsNameIgnoreCase("PENCIL").ids())
    }

    @Test
    fun testCountExistsAndDeleteByToManyProperty() {
        seedOrders()
        assertEquals(1L, orders.countByItemsName("Pen"))
        assertTrue(orders.existsByItemsName("pencil"))
        assertFalse(orders.existsByItemsName("Nothing"))

        assertEquals(0L, orders.deleteByItemsName("Nothing"))
        // the items and comments of the deleted order are removed by ON DELETE CASCADE
        assertEquals(1L, orders.deleteByItemsName("pencil"))
        assertEquals(listOf(first.id, third.id), orders.findAll().ids())
    }

    @Test
    fun testPageAndSliceByToManyProperty() {
        seedOrders()
        item(third, "Pen", 1, Products.selectAll().first()[Products.id])
        val page = orders.findPageByItemsName("Pen", PageRequest.of(0, 1, Sort.by("id")))
        assertEquals(listOf(first.id), page.content.ids())
        assertEquals(2L, page.totalElements)

        val slice = orders.findSliceByItemsName("Pen", PageRequest.of(1, 1, Sort.by("id")))
        assertEquals(listOf(third.id), slice.content.ids())
        assertFalse(slice.hasNext())
    }

    @Test
    fun testExplicitForeignKeyColumn() {
        seedOrders()
        // comments are matched by `order`, not by `replyTo`
        assertEquals(listOf(second.id), orders.findByCommentsText("slow").ids())
        assertEquals(listOf(first.id), orders.findByCommentsText("fast").ids())
    }

    // ---- multi-hop, to-one ----

    @Test
    fun testMultiHopOverTableWithoutId() {
        seedOrders()
        assertEquals(listOf(first.id), orders.findByItemsProductName("Ink").ids())
        assertEquals(listOf(second.id), orders.findByItemsProductNameStartingWith("Wo").ids())
        assertTrue(orders.findByItemsProductName("Steel").isEmpty())
    }

    @Test
    fun testForeignKeyColumnOfChildAsLeaf() {
        seedOrders()
        val paper = Products.selectAll().first { it[Products.name] == "Paper" }[Products.id].value
        assertEquals(listOf(first.id), orders.findByItemsProduct(paper).ids())
    }

    @Test
    fun testSortByNestedPropertyIsRejected() {
        seedOrders()
        assertThrows<Exception> { orders.findPageByItemsName("Pen", PageRequest.of(0, 1, Sort.by("items.name"))) }
    }
}

@ExposedRepository(table = Customers::class)
interface JoinCustomerRepository : ExposedCrudRepository<Customer, Long>

@ExposedRepository(table = Orders::class)
interface OrderRepository : ExposedCrudRepository<Order, Long> {
    fun findByItemsName(name: String): List<Order>
    fun findByItemsNameAndNote(name: String, note: String): List<Order>
    fun findByItemsNameOrNote(name: String, note: String): List<Order>
    fun findByItemsQuantityGreaterThan(quantity: Int): List<Order>
    fun findByItemsNameContainingIgnoreCase(name: String): List<Order>
    fun findByItemsNameIgnoreCase(name: String): List<Order>
    fun countByItemsName(name: String): Long
    fun existsByItemsName(name: String): Boolean
    fun deleteByItemsName(name: String): Long
    fun findPageByItemsName(name: String, pageable: Pageable): Page<Order>
    fun findSliceByItemsName(name: String, pageable: Pageable): Slice<Order>
    fun findByCommentsText(text: String): List<Order>
    fun findByItemsProductName(name: String): List<Order>
    fun findByItemsProductNameStartingWith(prefix: String): List<Order>
    fun findByItemsProduct(product: Long): List<Order>
}

object Products : LongIdTable("products") {
    val name = varchar("name", 255)
}

object Orders : LongIdTable("orders") {
    val customer = reference("customer_id", Customers)
    val note = varchar("note", 255)
}

/** A table without an id column, referencing [Orders] and [Products]. */
object OrderItems : Table("order_items") {
    val order = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val product = reference("product_id", Products)
    val name = varchar("name", 255)
    val quantity = integer("quantity")
}

/** Has two foreign keys to [Orders], so [Order.comments] has to name the column. */
object OrderComments : Table("order_comments") {
    val order = reference("order_id", Orders, onDelete = ReferenceOption.CASCADE)
    val replyTo = optReference("reply_to_id", Orders, onDelete = ReferenceOption.SET_NULL)
    val text = varchar("text", 255)
}

data class Order(
    val id: Long? = null,
    val customer: Long,
    val note: String,
    @ExposedReferencedBy(OrderItems::class)
    val items: List<OrderItem> = emptyList(),
    @ExposedReferencedBy(OrderComments::class, column = "order")
    val comments: List<OrderComment> = emptyList(),
)

data class OrderItem(val name: String, val quantity: Int, val product: Product)

data class Product(val id: Long? = null, val name: String)

data class OrderComment(val text: String)
