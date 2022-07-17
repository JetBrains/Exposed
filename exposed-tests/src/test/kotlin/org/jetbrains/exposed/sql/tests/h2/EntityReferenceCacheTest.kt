package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.entityCache
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.demo.dao.Cities
import org.jetbrains.exposed.sql.tests.demo.dao.City
import org.jetbrains.exposed.sql.tests.demo.dao.User
import org.jetbrains.exposed.sql.tests.demo.dao.Users
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTests
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData
import org.jetbrains.exposed.sql.tests.shared.entities.VNumber
import org.jetbrains.exposed.sql.tests.shared.entities.VString
import org.jetbrains.exposed.sql.tests.shared.entities.ViaTestData
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EntityReferenceCacheTest : DatabaseTestsBase() {

    private val db by lazy {
        TestDB.H2.connect()
    }

    private val dbWithCache by lazy {
        TestDB.H2.connect {
            keepLoadedReferencesOutOfTransaction = true
        }
    }

    private fun executeOnH2(vararg tables: Table, body: () -> Unit) {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        var testWasStarted = false
        transaction(db) {
            SchemaUtils.create(*tables)
            testWasStarted = true
        }
        Assume.assumeTrue(testWasStarted)
        if (testWasStarted) {
            try {
                body()
            } finally {
                transaction(db) {
                    SchemaUtils.drop(*tables)
                }
            }
        }
    }

    @Test
    fun `test referenceOn works out of transaction`() {
        var y1: EntityTestsData.YEntity by Delegates.notNull()
        var b1: EntityTestsData.BEntity by Delegates.notNull()
        executeOnH2(EntityTestsData.XTable, EntityTestsData.YTable) {
            transaction(db) {
                y1 = EntityTestsData.YEntity.new {
                    this.x = true
                }
                b1 = EntityTestsData.BEntity.new {
                    this.b1 = true
                    this.y = y1
                }
            }
            assertFails { y1.b }
            assertFails { b1.y }

            transaction(dbWithCache) {
                y1.refresh()
                b1.refresh()
                assertEquals(b1.id, y1.b?.id)
                assertEquals(y1.id, b1.y?.id)
            }

            assertEquals(b1.id, y1.b?.id)
            assertEquals(y1.id, b1.y?.id)
        }
    }

    @Test
    fun `test referenceOn works out of transaction via with`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }
            assertFails { b1.posts.toList() }
            assertFails { p1.board?.id }
            assertFails { p2.board?.id }

            transaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                listOf(p1, p2).with(EntityTests.Post::board)
            }

            assertEquals(b1.id, p1.board?.id)
            assertEquals(b1.id, p2.board?.id)
        }
    }

    @Test
    fun `test referrersOn works out of transaction`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {

                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }

            assertFails { b1.posts.toList() }
            assertFails { p1.board?.id }
            assertFails { p2.board?.id }

            transaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                assertEquals(b1.id, p1.board?.id)
                assertEquals(b1.id, p2.board?.id)
                assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
            }

            assertEquals(b1.id, p1.board?.id)
            assertEquals(b1.id, p2.board?.id)
            assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
        }
    }

    @Test
    fun `test optionalReferrersOn works out of transaction via warmup`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }
            assertFails { b1.posts.toList() }
            assertFails { p1.board?.id }
            assertFails { p2.board?.id }

            transaction(dbWithCache) {
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
    fun `test referrersOn works out of transaction via warmup`() {
        var c1: City by Delegates.notNull()
        var u1: User by Delegates.notNull()
        var u2: User by Delegates.notNull()
        executeOnH2(Cities, Users) {
            transaction(dbWithCache) {
                c1 = City.new {
                    name = "Seoul"
                }
                u1 = User.new {
                    name = "a"
                    city = c1
                    age = 5
                }
                u2 = User.new {
                    name = "b"
                    city = c1
                    age = 27
                }
                City.all().with(City::users).toList()
            }
            assertEqualCollections(c1.users.map { it.id }, u1.id, u2.id)
        }
    }

    @Test
    fun `test via reference out of transaction`() {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            transaction(db) {
                n = VNumber.new { number = 10 }
                s1 = VString.new { text = "aaa" }
                s2 = VString.new { text = "bbb" }
                n.connectedStrings = SizedCollection(s1, s2)
            }

            assertFails { n.connectedStrings.toList() }
            transaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
        }
    }

    @Test
    fun `test via reference load out of transaction`() {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            transaction(db) {
                n = VNumber.new { number = 10 }
                s1 = VString.new { text = "aaa" }
                s2 = VString.new { text = "bbb" }
                n.connectedStrings = SizedCollection(s1, s2)
            }

            assertFails { n.connectedStrings.toList() }
            transaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                n.load(VNumber::connectedStrings)
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)

            transaction(dbWithCache) {
                n.connectedStrings = SizedCollection(s1)
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id)
                n.load(VNumber::connectedStrings)
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id)
            }
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
        var customer by Customer.referencedOn(Orders.customer)
        val items by OrderItem.referrersOn(OrderItems.order)
        companion object : IntEntityClass<Order>(Orders)
    }

    class OrderItem(id: EntityID<Int>) : IntEntity(id) {
        var sku by OrderItems.sku
        var order by Order.referencedOn(OrderItems.order)
        companion object : IntEntityClass<OrderItem>(OrderItems)
    }

    class Address(id: EntityID<Int>) : IntEntity(id) {
        var street by Addresses.street
        var customer by Customer.referencedOn(Addresses.customer)
        companion object : IntEntityClass<Address>(Addresses)
    }

    class Role(id: EntityID<Int>) : IntEntity(id) {
        var name by Roles.name

        companion object : IntEntityClass<Role>(Roles)
    }

    class CustomerRole(id: EntityID<Int>) : IntEntity(id) {
        var customer by Customer.referencedOn(CustomerRoles.customer)
        var role by Role.referencedOn(CustomerRoles.role)

        companion object : IntEntityClass<CustomerRole>(CustomerRoles)
    }

    @Test fun `dont flush indirectly related entities on insert`() {
        withTables(Customers, Orders, OrderItems, Addresses) {
            val customer1 = Customer.new { name = "Test" }
            val order1 = Order.new {
                customer = customer1
                ref = "Test"
            }

            val orderItem1 = OrderItem.new {
                order = order1
                sku = "Test"
            }

            assertEqualCollections(listOf(order1), customer1.orders.toList())
            assertEqualCollections(emptyList(), customer1.addresses.toList())
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))

            assertEquals(1, order1.items.toList().size)
            assertEquals(orderItem1, order1.items.single())
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            Address.new {
                customer = customer1
                street = "Test"
            }

            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            val customer2 = Customer.new { name = "Test2" }

            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNull(entityCache.getReferrers<Address>(customer2.id, Addresses.customer))
            assertNull(entityCache.getReferrers<Order>(customer2.id, Orders.customer))

            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))
        }
    }

    @Test fun `dont flush indirectly related entities on delete`() {
        withTables(Customers, Orders, OrderItems, Addresses) {
            val customer1 = Customer.new { name = "Test" }
            val order1 = Order.new {
                customer = customer1
                ref = "Test"
            }

            val order2 = Order.new {
                customer = customer1
                ref = "Test2"
            }

            OrderItem.new {
                order = order1
                sku = "Test"
            }

            val orderItem2 = OrderItem.new {
                order = order2
                sku = "Test2"
            }

            Address.new {
                customer = customer1
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

    @Test fun `dont flush indirectly related entities with inner table`() {
        withTables(Customers, Roles, CustomerRoles) {
            val customer1 = Customer.new { name = "Test" }
            val role1 = Role.new { name = "Test" }
            val customerRole1 = CustomerRole.new {
                customer = customer1
                role = role1
            }

            flushCache()
            assertEqualCollections(listOf(customerRole1), customer1.customerRoles.toList())
            val role2 = Role.new { name = "Test2" }

            flushCache()
            assertNotNull(entityCache.getReferrers<CustomerRole>(customer1.id, CustomerRoles.customer))

            val customerRole2 = CustomerRole.new {
                customer = customer1
                role = role2
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
