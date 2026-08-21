package org.jetbrains.exposed.v1.dao.r2dbc.tests.h2

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.entityCache
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.dao.r2dbc.relationships.load
import org.jetbrains.exposed.v1.dao.r2dbc.relationships.with
import org.jetbrains.exposed.v1.dao.r2dbc.tests.demo.dao.Cities
import org.jetbrains.exposed.v1.dao.r2dbc.tests.demo.dao.City
import org.jetbrains.exposed.v1.dao.r2dbc.tests.demo.dao.User
import org.jetbrains.exposed.v1.dao.r2dbc.tests.demo.dao.Users
import org.jetbrains.exposed.v1.dao.r2dbc.tests.shared.EntityTests
import org.jetbrains.exposed.v1.dao.r2dbc.tests.shared.EntityTestsData
import org.jetbrains.exposed.v1.dao.r2dbc.tests.shared.VNumber
import org.jetbrains.exposed.v1.dao.r2dbc.tests.shared.VString
import org.jetbrains.exposed.v1.dao.r2dbc.tests.shared.ViaTestData
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EntityReferenceCacheTest : R2dbcDatabaseTestsBase() {
    private val db by lazy {
        TestDB.H2_V2.connect()
    }

    private val dbWithCache by lazy {
        TestDB.H2_V2.connect {
            keepLoadedReferencesOutOfTransaction = true
        }
    }

    private suspend fun executeOnH2(vararg tables: Table, body: suspend () -> Unit) {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        var testWasStarted = false
        suspendTransaction(db) {
            SchemaUtils.create(*tables)
            testWasStarted = true
        }
        Assumptions.assumeTrue(testWasStarted)
        if (testWasStarted) {
            try {
                body()
            } finally {
                suspendTransaction(db) {
                    SchemaUtils.drop(*tables)
                }
            }
        }
    }

    @Test
    fun `test referenceOn works out of transaction`() = runTest {
        var y1: EntityTestsData.YEntity by Delegates.notNull()
        var b1: EntityTestsData.BEntity by Delegates.notNull()
        executeOnH2(EntityTestsData.XTable, EntityTestsData.YTable) {
            suspendTransaction(db) {
                y1 = EntityTestsData.YEntity.newSuspend {
                    this.x = true
                }
                b1 = EntityTestsData.BEntity.newSuspend {
                    this.b1 = true
                    this.y.set(y1)
                }
            }
            assertFails { y1.b() }
            assertFails { b1.y() }

            suspendTransaction(dbWithCache) {
                y1.refresh()
                b1.refresh()
                assertEquals(b1.id, y1.b()?.id)
                assertEquals(y1.id, b1.y()?.id)
            }

            assertEquals(b1.id, y1.b()?.id)
            assertEquals(y1.id, b1.y()?.id)
        }
    }

    @Test
    fun `test backReferencedOn & optionalBackReferencedOn work out of transaction via load`() = runTest {
        var y1: EntityTestsData.YEntity by Delegates.notNull()
        var b1: EntityTestsData.BEntity by Delegates.notNull()
        executeOnH2(EntityTestsData.XTable, EntityTestsData.YTable) {
            suspendTransaction(db) {
                y1 = EntityTestsData.YEntity.newSuspend {}
                b1 = EntityTestsData.BEntity.newSuspend {
                    this.y.set(y1)
                }
            }
            // R2DBC: property access returns a `suspend () -> ...` accessor — only invocation
            // performs the DB lookup that must fail when there's no transaction.
            assertFails { y1.b() }
            assertFails { y1.bOpt() }

            suspendTransaction(dbWithCache) {
                y1.refresh()
                b1.refresh()
                y1.load(EntityTestsData.YEntity::b, EntityTestsData.YEntity::bOpt)
            }

            assertEquals(b1.id, y1.b()?.id)
            assertEquals(b1.id, y1.bOpt()?.id)
        }
    }

    @Test
    fun `test optionalBackReferencedOn and optionalReferencedOn work when value is missing`() = runTest {
        var y1: EntityTestsData.YEntity by Delegates.notNull()
        var b1: EntityTestsData.BEntity by Delegates.notNull()
        executeOnH2(EntityTestsData.XTable, EntityTestsData.YTable) {
            suspendTransaction(db) {
                y1 = EntityTestsData.YEntity.newSuspend {}
                b1 = EntityTestsData.BEntity.newSuspend {}
            }

            suspendTransaction(dbWithCache) {
                y1.refresh()
                b1.refresh()
                y1.load(EntityTestsData.YEntity::bOpt)
                b1.load(EntityTestsData.BEntity::y)
            }

            // R2DBC: property access returns the accessor lambda — invoke it to get the actual
            // (null) value pinned in the per-entity reference cache by `load(...)`.
            assertNull(y1.bOpt())
            assertNull(b1.y())
        }
    }

    @Test
    fun `test referenceOn works out of transaction via with`() = runTest {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts, EntityTests.Categories) {
            suspendTransaction(db) {
                b1 = EntityTests.Board.newSuspend {
                    name = "test-board"
                }
                p1 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
                p2 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
            }
            assertFails { b1.posts.toList() }
            assertFails { p1.board()?.id }
            assertFails { p2.board()?.id }

            suspendTransaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                listOf(p1, p2).with(EntityTests.Post::board)
            }

            assertEquals(b1.id, p1.board()?.id)
            assertEquals(b1.id, p2.board()?.id)
        }
    }

    @Test
    fun `test referrersOn works out of transaction`() = runTest {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts, EntityTests.Categories) {
            suspendTransaction(db) {
                b1 = EntityTests.Board.newSuspend {
                    name = "test-board"
                }
                p1 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
                p2 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
            }

            assertFails { b1.posts.toList() }
            assertFails { p1.board()?.id }
            assertFails { p2.board()?.id }

            suspendTransaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                assertEquals(b1.id, p1.board()?.id)
                assertEquals(b1.id, p2.board()?.id)
                assertEqualCollections(b1.posts.map { it.id }.toList(), p1.id, p2.id)
            }

            assertEquals(b1.id, p1.board()?.id)
            assertEquals(b1.id, p2.board()?.id)
            assertEqualCollections(b1.posts.map { it.id }.toList(), p1.id, p2.id)
        }
    }

    @Test
    fun `test optionalReferrersOn works out of transaction via warmup`() = runTest {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts, EntityTests.Categories) {
            suspendTransaction(db) {
                b1 = EntityTests.Board.newSuspend {
                    name = "test-board"
                }
                p1 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
                p2 = EntityTests.Post.newSuspend {
                    board.set(b1)
                }
            }
            assertFails { b1.posts.toList() }
            assertFails { p1.board()?.id }
            assertFails { p2.board()?.id }

            suspendTransaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                b1.load(EntityTests.Board::posts)
                assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
            }

            assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
        }
    }

    @Test
    fun `test referrersOn works out of transaction via warmup`() = runTest {
        var c1: City by Delegates.notNull()
        var u1: User by Delegates.notNull()
        var u2: User by Delegates.notNull()
        executeOnH2(Cities, Users) {
            suspendTransaction(dbWithCache) {
                c1 = City.newSuspend {
                    name = "Seoul"
                }
                u1 = User.newSuspend {
                    name = "a"
                    city.set(c1)
                    age = 5
                }
                u2 = User.newSuspend {
                    name = "b"
                    city.set(c1)
                    age = 27
                }
                City.all().with(City::users).toList()
            }
            assertEqualCollections(c1.users.map { it.id }.toList(), u1.id, u2.id)
        }
    }

    @Test
    fun `test via reference out of transaction`() = runTest {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            suspendTransaction(db) {
                n = VNumber.newSuspend { number = 10 }
                s1 = VString.newSuspend { text = "aaa" }
                s2 = VString.newSuspend { text = "bbb" }
                n.connectedStrings = SizedCollection(listOf(s1, s2))
            }

            assertFails { n.connectedStrings.toList() }
            suspendTransaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                assertEqualCollections(n.connectedStrings.map { it.id }.toList(), s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
        }
    }

    @Test
    fun `test via reference load out of transaction`() = runTest {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            suspendTransaction(db) {
                n = VNumber.newSuspend { number = 10 }
                s1 = VString.newSuspend { text = "aaa" }
                s2 = VString.newSuspend { text = "bbb" }
                n.connectedStrings = SizedCollection(listOf(s1, s2))
            }

            assertFails { n.connectedStrings.toList() }
            suspendTransaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                n.load(VNumber::connectedStrings)
                assertEqualCollections(n.connectedStrings.map { it.id }.toList(), s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }.toList(), s1.id, s2.id)

            suspendTransaction(dbWithCache) {
                n.connectedStrings = SizedCollection(listOf(s1))
                assertEqualCollections(n.connectedStrings.map { it.id }.toList(), s1.id)
                n.load(VNumber::connectedStrings)
                assertEqualCollections(n.connectedStrings.map { it.id }.toList(), s1.id)
            }
        }
    }

    /**
     * The reference cache is only populated when `keepLoadedReferencesOutOfTransaction` is enabled, so
     * handing back the raw cache entry here would yield `null` typed as a non-null `SizedIterable` and
     * surface later as an opaque NPE. Hence the explicit error.
     */
    @Test
    fun `test via reference out of transaction without cache reports a usage error`() = runTest {
        var n: VNumber by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            suspendTransaction(db) {
                n = VNumber.newSuspend { number = 10 }
                val s1 = VString.newSuspend { text = "aaa" }
                n.connectedStrings = SizedCollection(listOf(s1))
            }

            val failure = assertFailsWith<IllegalStateException> {
                n.connectedStrings.toList()
            }
            val message = assertNotNull(failure.message)
            assertTrue(
                message.contains("not in the entity cache") &&
                    message.contains("keepLoadedReferencesOutOfTransaction"),
                "expected a message naming the cause and the remedy, got: $message"
            )
        }
    }

    object Customers : IntIdTable() {
        val name = varchar("name", 10)
    }

    object Orders : IntIdTable() {
        val customer = reference("customer", Customers)
        val ref = varchar("name", 10)
    }

    object OrderItems : IntIdTable() {
        val order = reference("order", Orders)
        val sku = varchar("sky", 10)
    }

    object Addresses : IntIdTable() {
        val customer = reference("customer", Customers)
        val street = varchar("street", 10)
    }

    object Roles : IntIdTable() {
        val name = varchar("name", 10)
    }

    object CustomerRoles : IntIdTable() {
        val customer = reference("customer", Customers, onDelete = ReferenceOption.CASCADE)
        val role = reference("role", Roles, onDelete = ReferenceOption.CASCADE)
    }

    class Customer(id: EntityID<Int>) : IntEntity(id) {
        var name by Customers.name
        val orders by Order.referrersOn(Orders.customer)
        val addresses by Address.referrersOn(Addresses.customer)
        val customerRoles by CustomerRole.referrersOn(CustomerRoles.customer)

        companion object : IntEntityClass<Customer>(Customers)
    }

    class Order(id: EntityID<Int>) : IntEntity(id) {
        var ref by Orders.ref
        val customer by Customer.referencedOn(Orders.customer)
        val items by OrderItem.referrersOn(OrderItems.order)

        companion object : IntEntityClass<Order>(Orders)
    }

    class OrderItem(id: EntityID<Int>) : IntEntity(id) {
        var sku by OrderItems.sku
        val order by Order.referencedOn(OrderItems.order)

        companion object : IntEntityClass<OrderItem>(OrderItems)
    }

    class Address(id: EntityID<Int>) : IntEntity(id) {
        var street by Addresses.street
        val customer by Customer.referencedOn(Addresses.customer)

        companion object : IntEntityClass<Address>(Addresses)
    }

    class Role(id: EntityID<Int>) : IntEntity(id) {
        var name by Roles.name

        companion object : IntEntityClass<Role>(Roles)
    }

    class CustomerRole(id: EntityID<Int>) : IntEntity(id) {
        val customer by Customer.referencedOn(CustomerRoles.customer)
        val role by Role.referencedOn(CustomerRoles.role)

        companion object : IntEntityClass<CustomerRole>(CustomerRoles)
    }

    @Test
    fun `dont flush indirectly related entities on insert`() {
        withTables(Customers, Orders, OrderItems, Addresses) {
            val customer1 = Customer.newSuspend { name = "Test" }
            val order1 = Order.newSuspend {
                customer.set(customer1)
                ref = "Test"
            }

            val orderItem1 = OrderItem.newSuspend {
                order.set(order1)
                sku = "Test"
            }

            assertEqualCollections(listOf(order1), customer1.orders.toList())
            assertEqualCollections(emptyList(), customer1.addresses.toList())
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))

            assertEquals(1, order1.items.toList().size)
            assertEquals(orderItem1, order1.items.single())
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            Address.newSuspend {
                customer.set(customer1)
                street = "Test"
            }

            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            val customer2 = Customer.newSuspend { name = "Test2" }

            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNull(entityCache.getReferrers<Address>(customer2.id, Addresses.customer))
            assertNull(entityCache.getReferrers<Order>(customer2.id, Orders.customer))

            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))
        }
    }

    @Test
    fun `dont flush indirectly related entities on delete`() {
        withTables(Customers, Orders, OrderItems, Addresses) {
            val customer1 = Customer.newSuspend { name = "Test" }
            val order1 = Order.newSuspend {
                customer.set(customer1)
                ref = "Test"
            }

            val order2 = Order.newSuspend {
                customer.set(customer1)
                ref = "Test2"
            }

            OrderItem.newSuspend {
                order.set(order1)
                sku = "Test"
            }

            val orderItem2 = OrderItem.newSuspend {
                order.set(order2)
                sku = "Test2"
            }

            Address.newSuspend {
                customer.set(customer1)
                street = "Test"
            }

            flushCache()

            // Load caches
            customer1.orders.toList()
            customer1.addresses.toList()
            order1.items.toList()
            order2.items.toList()

            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))
            assertNotNull(entityCache.getReferrers<OrderItem>(order2.id, OrderItems.order))

            orderItem2.delete()

            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))
            assertNull(entityCache.getReferrers<OrderItem>(order2.id, OrderItems.order))

            // Load caches
            customer1.orders.toList()
            customer1.addresses.toList()
            order1.items.toList()
            order2.items.toList()

            order2.delete()
            assertNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))
            assertNull(entityCache.getReferrers<OrderItem>(order2.id, OrderItems.order))
        }
    }

    @Test
    fun `dont flush indirectly related entities with inner table`() {
        withTables(Customers, Roles, CustomerRoles) {
            val customer1 = Customer.newSuspend { name = "Test" }
            val role1 = Role.newSuspend { name = "Test" }
            val customerRole1 = CustomerRole.newSuspend {
                customer.set(customer1)
                role.set(role1)
            }

            flushCache()
            assertEqualCollections(listOf(customerRole1), customer1.customerRoles.toList())
            val role2 = Role.newSuspend { name = "Test2" }

            flushCache()
            assertNotNull(entityCache.getReferrers<CustomerRole>(customer1.id, CustomerRoles.customer))

            val customerRole2 = CustomerRole.newSuspend {
                customer.set(customer1)
                role.set(role2)
            }
            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))

            assertEqualCollections(listOf(customerRole1, customerRole2), customer1.customerRoles.toList())
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))

            role2.delete()
            assertNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))
        }
    }
}
