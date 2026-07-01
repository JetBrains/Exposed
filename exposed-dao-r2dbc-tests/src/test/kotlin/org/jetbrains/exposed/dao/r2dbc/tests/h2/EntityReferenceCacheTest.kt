package org.jetbrains.exposed.dao.r2dbc.tests.h2

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.dao.r2dbc.tests.demo.dao.Cities
import org.jetbrains.exposed.dao.r2dbc.tests.demo.dao.City
import org.jetbrains.exposed.dao.r2dbc.tests.demo.dao.User
import org.jetbrains.exposed.dao.r2dbc.tests.demo.dao.Users
import org.jetbrains.exposed.dao.r2dbc.tests.shared.EntityTests
import org.jetbrains.exposed.dao.r2dbc.tests.shared.EntityTestsData
import org.jetbrains.exposed.dao.r2dbc.tests.shared.VNumber
import org.jetbrains.exposed.dao.r2dbc.tests.shared.VString
import org.jetbrains.exposed.dao.r2dbc.tests.shared.ViaTestData
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.load
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
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
import kotlin.test.assertNotNull

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
                y1 = EntityTestsData.YEntity.new {
                    this.x = true
                }.flush()
                b1 = EntityTestsData.BEntity.new {
                    this.b1 = true
                    this.y.set(y1)
                }.flush()
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
                y1 = EntityTestsData.YEntity.new {}.flush()
                b1 = EntityTestsData.BEntity.new {
                    this.y.set(y1)
                }.flush()
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
                y1 = EntityTestsData.YEntity.new {}.flush()
                b1 = EntityTestsData.BEntity.new {}.flush()
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
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }.flush()
                p1 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
                p2 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
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
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }.flush()
                p1 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
                p2 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
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
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }.flush()
                p1 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
                p2 = EntityTests.Post.new {
                    board.set(b1)
                }.flush()
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
                c1 = City.new {
                    name = "Seoul"
                }.flush()
                u1 = User.new {
                    name = "a"
                    city.set(c1)
                    age = 5
                }.flush()
                u2 = User.new {
                    name = "b"
                    city.set(c1)
                    age = 27
                }.flush()
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
                n = VNumber.new { number = 10 }.flush()
                s1 = VString.new { text = "aaa" }.flush()
                s2 = VString.new { text = "bbb" }.flush()
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
                n = VNumber.new { number = 10 }.flush()
                s1 = VString.new { text = "aaa" }.flush()
                s2 = VString.new { text = "bbb" }.flush()
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
            val customer1 = Customer.new { name = "Test" }.flush()
            val order1 = Order.new {
                customer.set(customer1)
                ref = "Test"
            }.flush()

            val orderItem1 = OrderItem.new {
                order.set(order1)
                sku = "Test"
            }.flush()

            assertEqualCollections(listOf(order1), customer1.orders.toList())
            assertEqualCollections(emptyList(), customer1.addresses.toList())
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))

            assertEquals(1, order1.items.toList().size)
            assertEquals(orderItem1, order1.items.single())
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            Address.new {
                customer.set(customer1)
                street = "Test"
            }.flush()

            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, Addresses.customer))
            assertNotNull(entityCache.getReferrers<Order>(customer1.id, Orders.customer))
            assertNotNull(entityCache.getReferrers<OrderItem>(order1.id, OrderItems.order))

            val customer2 = Customer.new { name = "Test2" }.flush()

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
            val customer1 = Customer.new { name = "Test" }.flush()
            val order1 = Order.new {
                customer.set(customer1)
                ref = "Test"
            }.flush()

            val order2 = Order.new {
                customer.set(customer1)
                ref = "Test2"
            }.flush()

            OrderItem.new {
                order.set(order1)
                sku = "Test"
            }.flush()

            val orderItem2 = OrderItem.new {
                order.set(order2)
                sku = "Test2"
            }.flush()

            Address.new {
                customer.set(customer1)
                street = "Test"
            }.flush()

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
            val customer1 = Customer.new { name = "Test" }.flush()
            val role1 = Role.new { name = "Test" }.flush()
            val customerRole1 = CustomerRole.new {
                customer.set(customer1)
                role.set(role1)
            }.flush()

            flushCache()
            assertEqualCollections(listOf(customerRole1), customer1.customerRoles.toList())
            val role2 = Role.new { name = "Test2" }.flush()

            flushCache()
            assertNotNull(entityCache.getReferrers<CustomerRole>(customer1.id, CustomerRoles.customer))

            val customerRole2 = CustomerRole.new {
                customer.set(customer1)
                role.set(role2)
            }.flush()
            flushCache()

            assertNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))

            assertEqualCollections(listOf(customerRole1, customerRole2), customer1.customerRoles.toList())
            assertNotNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))

            role2.delete()
            assertNull(entityCache.getReferrers<Address>(customer1.id, CustomerRoles.customer))
        }
    }
}
