package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappings
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedTableRegistry
import org.jetbrains.exposed.v1.spring.data.mapping.JoinRelation
import org.jetbrains.exposed.v1.spring.data.mapping.OwnedRelation
import org.jetbrains.exposed.v1.spring.data.query.PropertyPathResolver
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.core.PropertyPath
import org.springframework.transaction.support.TransactionTemplate

/**
 * To-many relations without `@ExposedReferencedBy`: they are inferred from the foreign keys of the scanned tables.
 * `Shop.orders` and `ShopOrder.items` are owned (one-to-many), `ShopOrder.tags` goes through the join table [ShopOrderTags],
 * `ShopEmployee.reports` references its own table.
 *
 * The ambiguous and unresolvable cases use nested repositories, created by an [ExposedRepositoryFactory] with an explicit registry.
 */
open class InferredRelationRepositoryTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: InferredCustomerRepository

    @Autowired
    lateinit var shops: ShopRepository

    @Autowired
    lateinit var employees: ShopEmployeeRepository

    @BeforeEach
    fun createShopTables() = TransactionTemplate(tm).executeWithoutResult {
        SchemaUtils.create(Shops, ShopOrders, ShopOrderItems, Tags, ShopOrderTags, ShopEmployees, Accounts, Transfers, AddressBooks, Depots, DepotBins, DepotSlots)
        SchemaUtils.create(Boards, BoardTagLinks, BoardFeaturedTags)
    }

    @AfterEach
    fun dropShopTables() = TransactionTemplate(tm).executeWithoutResult {
        SchemaUtils.drop(BoardFeaturedTags, BoardTagLinks, Boards)
        SchemaUtils.drop(DepotSlots, DepotBins, Depots, AddressBooks, Transfers, Accounts, ShopEmployees, ShopOrderTags, Tags, ShopOrderItems, ShopOrders, Shops)
    }

    private fun shop(name: String): EntityID<Long> = Shops.insertAndGetId { it[Shops.name] = name }

    private fun order(shop: EntityID<Long>, number: String): EntityID<Long> = ShopOrders.insertAndGetId {
        it[ShopOrders.shop] = shop
        it[ShopOrders.number] = number
    }

    private fun item(order: EntityID<Long>, article: String, quantity: Int) {
        ShopOrderItems.insert {
            it[ShopOrderItems.order] = order
            it[ShopOrderItems.article] = article
            it[ShopOrderItems.quantity] = quantity
        }
    }

    private fun tag(order: EntityID<Long>, tag: EntityID<Long>) {
        ShopOrderTags.insert {
            it[ShopOrderTags.order] = order
            it[ShopOrderTags.tag] = tag
        }
    }

    private fun newTag(name: String): EntityID<Long> = Tags.insertAndGetId { it[Tags.name] = name }

    // ---- reading ----

    @Test
    fun testLoadsInferredOwnedCollections() {
        val pens = shop("Pens")
        shop("Empty")
        val first = order(pens, "A-1")
        val second = order(pens, "A-2")
        item(first, "Pen", 2)
        item(first, "Ink", 1)
        item(second, "Pencil", 5)

        val loaded = shops.findById(pens.value).get()
        assertEquals(listOf("A-1", "A-2"), loaded.orders.map { it.number })
        assertEquals(listOf("Pen" to 2, "Ink" to 1), loaded.orders[0].items.map { it.article to it.quantity })
        assertEquals(listOf("Pencil"), loaded.orders[1].items.map { it.article })
        assertTrue(loaded.orders.flatMap { it.items }.all { it.id != null })

        assertEquals(emptyList<ShopOrder>(), shops.findAll().single { it.name == "Empty" }.orders)
    }

    @Test
    fun testLoadsInferredJoinCollections() {
        val shop = shop("Pens")
        val first = order(shop, "A-1")
        val second = order(shop, "A-2")
        val urgent = newTag("urgent")
        val gift = newTag("gift")
        tag(first, urgent)
        tag(first, gift)
        tag(second, gift)

        val orders = shops.findById(shop.value).get().orders
        assertEquals(setOf("urgent", "gift"), orders[0].tags.map { it.name }.toSet())
        assertEquals(setOf(Tag(gift.value, "gift")), orders[1].tags)
    }

    @Test
    fun testLoadsSelfReferencingCollection() {
        val boss = ShopEmployees.insertAndGetId { it[name] = "Boss" }
        val lead = ShopEmployees.insertAndGetId {
            it[name] = "Lead"
            it[manager] = boss
        }
        ShopEmployees.insert {
            it[name] = "Dev"
            it[manager] = lead
        }

        val loaded = employees.findById(boss.value).get()
        assertEquals(listOf("Lead"), loaded.reports.map { it.name })
        assertEquals(listOf("Dev"), loaded.reports.single().reports.map { it.name })
        assertEquals(emptyList<ShopEmployee>(), loaded.reports.single().reports.single().reports)
    }

    // ---- saving ----

    @Test
    fun testSaveSyncsInferredOwnedRows() {
        val saved = shops.save(
            Shop(
                name = "Pens",
                orders = listOf(
                    ShopOrder(number = "A-1", items = listOf(ShopOrderItem(article = "Pen", quantity = 2), ShopOrderItem(article = "Ink", quantity = 1))),
                    ShopOrder(number = "A-2", items = listOf(ShopOrderItem(article = "Pencil", quantity = 5))),
                ),
            )
        )
        assertEquals(saved, shops.findById(saved.id!!).get())
        assertEquals(listOf("A-1", "A-2"), ShopOrders.selectAll().map { it[ShopOrders.number] })
        assertEquals(setOf("Pen", "Ink", "Pencil"), articles())

        val (first, second) = saved.orders
        val pen = first.items.first()
        shops.save(
            saved.copy(
                orders = listOf(
                    first.copy(number = "A-1b", items = listOf(pen.copy(quantity = 3), ShopOrderItem(article = "Pad", quantity = 1))),
                    ShopOrder(number = "A-3"),
                ),
            )
        )

        val loaded = shops.findById(saved.id).get()
        assertEquals(listOf("A-1b", "A-3"), loaded.orders.map { it.number })
        // kept rows are updated, not deleted and inserted again
        assertEquals(first.id, loaded.orders[0].id)
        assertEquals(pen.id, loaded.orders[0].items[0].id)
        assertEquals(listOf("Pen" to 3, "Pad" to 1), loaded.orders[0].items.map { it.article to it.quantity })
        // the removed order is deleted, its items (Pencil) follow through ON DELETE CASCADE
        assertFalse(ShopOrders.selectAll().any { it[ShopOrders.id].value == second.id })
        assertEquals(setOf("Pen", "Pad"), articles())
    }

    @Test
    fun testOrphanRemovalDeletesOnlyTheRemovedRows() {
        val saved = shops.save(Shop(name = "Pens", orders = listOf(ShopOrder(number = "A-1", items = listOf(ShopOrderItem(article = "Pen", quantity = 1))))))
        val deletes = captureDeletes { shops.save(saved.copy(orders = emptyList())) }
        // the application deletes the order only, the database its items
        assertEquals(1, deletes.size) { deletes.toString() }
        assertTrue("shop_orders" in deletes.single().lowercase()) { deletes.toString() }
        assertEquals(0, ShopOrders.selectAll().count())
        assertEquals(emptySet<String>(), articles())
    }

    @Test
    fun testSaveDoesNotWriteJoinRows() {
        val gift = newTag("gift")
        val saved = shops.save(Shop(name = "Pens", orders = listOf(ShopOrder(number = "A-1", tags = setOf(Tag(gift.value, "gift"), Tag(name = "new"))))))
        assertEquals(0, ShopOrderTags.selectAll().count())
        assertEquals(1, Tags.selectAll().count())

        tag(EntityID(saved.orders.single().id!!, ShopOrders), gift)
        val loaded = shops.findById(saved.id!!).get()
        shops.save(loaded.copy(orders = listOf(loaded.orders.single().copy(tags = emptySet()))))
        assertEquals(setOf("gift"), shops.findById(saved.id).get().orders.single().tags.map { it.name }.toSet())
    }

    // ---- deleting: parent rows only, children follow the foreign key's ON DELETE ----

    @Test
    fun testDeleteCascadesThroughOnDelete() {
        val (a, b, c, d) = listOf("a", "b", "c", "d").map(::savedShop)
        val (e, f, g) = listOf("e", "f", "g").map(::savedShop)
        tag(EntityID(a.orders.single().id!!, ShopOrders), newTag("urgent"))

        val deletes = captureDeletes {
            shops.deleteById(a.id!!)
            assertEquals(0, ShopOrderTags.selectAll().count())
            assertEquals(1, Tags.selectAll().count())
            shops.delete(b)
            shops.deleteAllById(listOf(c.id!!))
            shops.deleteAll(listOf(d))
            assertEquals(1L, shops.deleteByName(e.name))
            // a derived delete returning the entities loads them, with their owned rows, before deleting
            assertEquals(listOf(f), shops.deleteAllByName(f.name))
            assertEquals(listOf(g.orders.single().number), ShopOrders.selectAll().map { it[ShopOrders.number] })
            assertEquals(setOf("Pen g"), articles())

            shops.deleteAll()
            assertEquals(0, ShopOrders.selectAll().count())
            assertEquals(emptySet<String>(), articles())
        }
        assertEquals(7, deletes.size) { deletes.toString() }
        assertTrue(deletes.all { "shops" in it.lowercase() && "shop_" !in it.lowercase() }) { deletes.toString() }
    }

    @Test
    fun testDeleteFailsWhenOnDeleteRestricts() {
        val depots = ExposedRepositoryFactory(tables = ExposedTableRegistry(listOf(DepotBins, DepotSlots))).getRepository(DepotRepository::class.java)
        val depot = depots.save(Depot(name = "North", bins = listOf(DepotBin(label = "B1", slots = listOf(DepotSlot(code = "S1"))))))

        assertRestricted { depots.deleteById(depot.id!!) }
        assertRestricted { depots.delete(depot) }
        assertRestricted { depots.deleteAllById(listOf(depot.id!!)) }
        assertRestricted { depots.deleteAll(listOf(depot)) }
        assertRestricted { depots.deleteAll() }
        assertRestricted { depots.deleteByName(depot.name) }
        // orphan removal deletes the removed bin only, its slot restricts that too
        assertRestricted { depots.save(depot.copy(bins = emptyList())) }

        assertEquals(depot, depots.findById(depot.id!!).get())
        assertEquals(1, DepotSlots.selectAll().count())
    }

    private fun assertRestricted(delete: () -> Unit) {
        val exception = assertThrows<Exception> { delete() }
        val messages = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { "Referential integrity constraint violation" in it }) { messages.toString() }
    }

    // ---- derived queries through inferred relations ----

    /** Pens: A-1 (Pen x2, Ink x1) tagged urgent, A-2 (Pencil x5); Pads: B-1 (Pen x1); Shelf: C-1 without items; Empty: no orders. */
    private fun seedShops() {
        val pens = shop("Pens")
        val pads = shop("Pads")
        val shelf = shop("Shelf")
        shop("Empty")
        val first = order(pens, "A-1")
        item(first, "Pen", 2)
        item(first, "Ink", 1)
        item(order(pens, "A-2"), "Pencil", 5)
        item(order(pads, "B-1"), "Pen", 1)
        order(shelf, "C-1")
        tag(first, newTag("urgent"))
    }

    private fun Iterable<Shop>.shopNames() = map { it.name }.sorted()

    @Test
    fun testFindByNestedOwnedPath() {
        seedShops()
        assertEquals(listOf("Pads", "Pens"), shops.findByOrdersItemsArticle("Pen").shopNames())
        assertEquals(listOf("Pens"), shops.findByOrdersItemsArticle("Pencil").shopNames())
        assertEquals(emptyList<String>(), shops.findByOrdersItemsArticle("Pad").shopNames())
        // the matching shops are returned with all their orders and items
        assertEquals(listOf("A-1", "A-2"), shops.findByOrdersItemsArticle("Pencil").single().orders.map { it.number })
        assertEquals(1L, shops.countByOrdersItemsArticle("Pencil"))
    }

    @Test
    fun testPartsOnTheSamePathMatchTheSameRow() {
        seedShops()
        // Pens has a Pen (x2) and an item with quantity 1 (Ink), but no single item with both
        assertEquals(listOf("Pads"), shops.findByOrdersItemsArticleAndOrdersItemsQuantity("Pen", 1).shopNames())
        assertEquals(listOf("Pens"), shops.findByOrdersItemsArticleAndOrdersItemsQuantity("Pen", 2).shopNames())
        assertEquals(emptyList<String>(), shops.findByOrdersItemsArticleAndOrdersItemsQuantity("Ink", 2).shopNames())

        val selects = captureSelects { shops.findByOrdersItemsArticleAndOrdersItemsQuantity("Pen", 1) }
        val query = selects.first()
        // one subquery per hop: shops -> shop_orders -> shop_order_items, both conditions in the innermost one
        assertEquals(1, Regex("FROM SHOP_ORDERS\\b", RegexOption.IGNORE_CASE).findAll(query).count()) { query }
        assertEquals(1, Regex("FROM SHOP_ORDER_ITEMS\\b", RegexOption.IGNORE_CASE).findAll(query).count()) { query }
        assertTrue(Regex("SHOP_ORDER_ITEMS\\.ARTICLE = \\?\\) AND \\(SHOP_ORDER_ITEMS\\.QUANTITY = \\?", RegexOption.IGNORE_CASE).containsMatchIn(query)) { query }
        println("findByOrdersItemsArticleAndOrdersItemsQuantity: $query")
    }

    @Test
    fun testFindThroughInferredJoinRelation() {
        seedShops()
        assertEquals(listOf("Pens"), shops.findByOrdersTagsName("urgent").shopNames())
        assertEquals(emptyList<String>(), shops.findByOrdersTagsName("gift").shopNames())
        val query = captureSelects { shops.findByOrdersTagsName("urgent") }.first()
        assertTrue("SHOP_ORDER_TAGS" in query.uppercase() && "FROM TAGS" in query.uppercase()) { query }
        println("findByOrdersTagsName: $query")
    }

    @Test
    fun testIsEmptyAndIsNotEmptyOnToManyPaths() {
        seedShops()
        assertEquals(listOf("Empty"), shops.findByOrdersIsEmpty().shopNames())
        assertEquals(listOf("Pads", "Pens", "Shelf"), shops.findByOrdersIsNotEmpty().shopNames())
        // a shop with at least one order that has items / has no items
        assertEquals(listOf("Pads", "Pens"), shops.findByOrdersItemsIsNotEmpty().shopNames())
        assertEquals(listOf("Shelf"), shops.findByOrdersItemsIsEmpty().shopNames())
        // through the join table
        assertEquals(listOf("Pens"), shops.findByOrdersTagsIsNotEmpty().shopNames())
        assertEquals(listOf("Pads", "Pens", "Shelf"), shops.findByOrdersTagsIsEmpty().shopNames())
        // combined with other parts, sharing the prefix of the path: the same order has the number and no items
        assertEquals(listOf("Shelf"), shops.findByOrdersNumberAndOrdersItemsIsEmpty("C-1").shopNames())
        assertEquals(emptyList<String>(), shops.findByOrdersNumberAndOrdersItemsIsEmpty("A-1").shopNames())
        assertEquals(listOf("Empty", "Pads"), shops.findByOrdersIsEmptyOrName("Pads").shopNames())
        // sorting is unaffected
        assertEquals(listOf("Shelf", "Pens", "Pads"), shops.findByOrdersIsNotEmptyOrderByNameDesc().map { it.name })
        assertEquals(1L, shops.countByOrdersIsEmpty())
        assertTrue(shops.existsByOrdersItemsIsEmpty())

        val isEmpty = captureSelects { shops.findByOrdersIsEmpty() }.first()
        val isNotEmpty = captureSelects { shops.findByOrdersItemsIsNotEmpty() }.first()
        val shared = captureSelects { shops.findByOrdersNumberAndOrdersItemsIsEmpty("C-1") }.first()
        assertTrue(Regex("NOT IN \\(SELECT SHOP_ORDERS\\.SHOP_ID FROM SHOP_ORDERS", RegexOption.IGNORE_CASE).containsMatchIn(isEmpty)) { isEmpty }
        assertFalse("NOT IN" in isNotEmpty.uppercase()) { isNotEmpty }
        assertEquals(1, Regex("FROM SHOP_ORDERS\\b", RegexOption.IGNORE_CASE).findAll(shared).count()) { shared }
        println("findByOrdersIsEmpty: $isEmpty")
        println("findByOrdersItemsIsNotEmpty: $isNotEmpty")
        println("findByOrdersNumberAndOrdersItemsIsEmpty: $shared")
    }

    @Test
    fun testIsEmptyOnSelfReferenceIgnoresNullForeignKeys() {
        val boss = ShopEmployees.insertAndGetId { it[name] = "Boss" }
        val lead = ShopEmployees.insertAndGetId {
            it[name] = "Lead"
            it[manager] = boss
        }
        ShopEmployees.insert {
            it[name] = "Dev"
            it[manager] = lead
        }
        // the boss has no manager: a NULL in the subquery must not make NOT IN match nothing
        assertEquals(listOf("Dev"), employees.findByReportsIsEmpty().map { it.name })
        assertEquals(listOf("Boss", "Lead"), employees.findByReportsIsNotEmpty().map { it.name }.sorted())
        assertEquals(listOf("Boss"), employees.findByReportsReportsIsNotEmpty().map { it.name })
    }

    @Test
    fun testAmbiguousHopFailsAtCreationWithThePath() {
        // the aggregate reads BankAccount.outgoing too, so creating the repository already fails while reading it
        val messages = creationFailure(BankRepository::class.java, BankAccounts, BankTransfers)
        assertMentions(messages, "BankAccount.outgoing", "BankTransfers.from", "BankTransfers.to", "column")

        // resolving the query path names the path
        val mappings = ExposedMappings(tables = ExposedTableRegistry(listOf(BankAccounts, BankTransfers)))
        val paths = PropertyPathResolver(EntityMetadata(Banks, mappings.persistentEntity(Bank::class.java), mappings), mappings)
        val exception = assertThrows<IllegalArgumentException> { paths.resolve(PropertyPath.from("accounts.outgoing.amount", Bank::class.java)) }
        assertMentions(listOf(exception.message!!), "BankAccount.outgoing", "column", "path 'accounts.outgoing.amount'")
        val toMany = assertThrows<IllegalArgumentException> { paths.resolveToMany(PropertyPath.from("accounts.outgoing", Bank::class.java)) }
        assertMentions(listOf(toMany.message!!), "BankAccount.outgoing", "path 'accounts.outgoing'")
    }

    @Test
    fun testIsEmptyOnScalarPathFailsAtCreation() {
        val messages = creationFailure(ScalarIsEmptyShopRepository::class.java, ShopOrders, ShopOrderItems, ShopOrderTags)
        assertMentions(messages, "IS_EMPTY", "orders.number")
    }

    private fun savedShop(name: String): Shop =
        shops.save(Shop(name = name, orders = listOf(ShopOrder(number = "$name-1", items = listOf(ShopOrderItem(article = "Pen $name", quantity = 1))))))

    private fun articles(): Set<String> = ShopOrderItems.selectAll().map { it[ShopOrderItems.article] }.toSet()

    // ---- resolution at repository creation ----

    @Test
    fun testAmbiguousForeignKeyFailsAtCreation() {
        val messages = creationFailure(AccountRepository::class.java, Transfers)
        assertMentions(messages, "Account.outgoing", "Transfers.from", "Transfers.to", "column")
    }

    @Test
    fun testAmbiguousJoinTargetFailsAtCreation() {
        val messages = creationFailure(BoardRepository::class.java, BoardTagLinks)
        assertMentions(messages, "Board.tags", "BoardTagLinks.primary", "BoardTagLinks.secondary", "target")
    }

    @Test
    fun testAnnotationOverridesInference() {
        val mappings = ExposedMappings(tables = ExposedTableRegistry(listOf(Transfers)))
        val relation = mappings.relationFor(Accounts, AnnotatedAccount::class.java, "outgoing")
        assertTrue(relation is OwnedRelation) { relation.toString() }
        assertSame(Transfers.from, relation!!.foreignKey)
        assertSame(Transfers, relation.table)

        val alice = Accounts.insertAndGetId { it[name] = "Alice" }
        val bob = Accounts.insertAndGetId { it[name] = "Bob" }
        transfer(alice, bob, 10)
        transfer(alice, bob, 11)
        transfer(bob, alice, 20)

        val accounts = ExposedRepositoryFactory(tables = ExposedTableRegistry(listOf(Transfers))).getRepository(AnnotatedAccountRepository::class.java)
        assertEquals(listOf(10, 11), accounts.findById(alice.value).get().outgoing.map { it.amount })
        assertEquals(listOf(20), accounts.findById(bob.value).get().outgoing.map { it.amount })
    }

    /** Board "Desk": one link with primary tag "urgent" and secondary tag "gift", and the featured tag "new". */
    private fun seedBoard(): EntityID<Long> {
        val desk = Boards.insertAndGetId { it[name] = "Desk" }
        val urgent = newTag("urgent")
        val gift = newTag("gift")
        BoardTagLinks.insert {
            it[board] = desk
            it[primary] = urgent
            it[secondary] = gift
        }
        BoardFeaturedTags.insert {
            it[board] = desk
            it[tag] = newTag("new")
        }
        return desk
    }

    @Test
    fun testAnnotationWithTargetResolvesAmbiguousJoin() {
        val registry = ExposedTableRegistry(listOf(BoardTagLinks))
        val relation = ExposedMappings(tables = registry).relationFor(Boards, PrimaryTagBoard::class.java, "tags")
        assertTrue(relation is JoinRelation) { relation.toString() }
        assertSame(BoardTagLinks, relation!!.table)
        assertSame(BoardTagLinks.board, relation.foreignKey)
        assertSame(BoardTagLinks.primary, (relation as JoinRelation).targetKey)

        val desk = seedBoard()
        val boards = ExposedRepositoryFactory(tables = registry).getRepository(PrimaryTagBoardRepository::class.java)
        assertEquals(setOf("urgent"), boards.findById(desk.value).get().tags.map { it.name }.toSet())
    }

    @Test
    fun testAnnotationReplacesInferredTarget() {
        // only the featured tags are registered: inference picks BoardFeaturedTags.tag
        val registry = ExposedTableRegistry(listOf(BoardFeaturedTags))
        val mappings = ExposedMappings(tables = registry)
        val inferred = mappings.relationFor(Boards, Board::class.java, "tags")
        assertSame(BoardFeaturedTags.tag, (inferred as JoinRelation).targetKey)
        // the annotation selects another join table and target, which need not be registered
        val annotated = mappings.relationFor(Boards, SecondaryTagBoard::class.java, "tags")
        assertSame(BoardTagLinks.secondary, (annotated as JoinRelation).targetKey)

        val desk = seedBoard()
        val factory = ExposedRepositoryFactory(tables = registry)
        assertEquals(setOf("new"), factory.getRepository(BoardRepository::class.java).findById(desk.value).get().tags.map { it.name }.toSet())
        assertEquals(setOf("gift"), factory.getRepository(SecondaryTagBoardRepository::class.java).findById(desk.value).get().tags.map { it.name }.toSet())
    }

    @Test
    fun testAmbiguityAcrossTablesAsksForTheTable() {
        val messages = creationFailure(BoardRepository::class.java, BoardTagLinks, BoardFeaturedTags)
        assertMentions(
            messages,
            "Board.tags is ambiguous, these relations to inferred_boards fit: " +
                "BoardTagLinks.board with target BoardTagLinks.primary (many-to-many to tags), " +
                "BoardTagLinks.board with target BoardTagLinks.secondary (many-to-many to tags), " +
                "BoardFeaturedTags.board with target BoardFeaturedTags.tag (many-to-many to tags). " +
                "Annotate it with @ExposedReferencedBy(BoardTagLinks::class or BoardFeaturedTags::class, target = \"...\") " +
                "to choose one (set the table and target)",
        )
    }

    private fun transfer(from: EntityID<Long>, to: EntityID<Long>, amount: Int) {
        Transfers.insert {
            it[Transfers.from] = from
            it[Transfers.to] = to
            it[Transfers.amount] = amount
        }
    }

    @Test
    fun testUnregisteredChildTableFailsAtCreation() {
        val messages = creationFailure(NestedShopRepository::class.java)
        assertMentions(messages, "Shop.orders", "@ExposedReferencedBy")
    }

    @Test
    fun testChildTableOnlyRegisteredResolves() {
        val factory = ExposedRepositoryFactory(tables = ExposedTableRegistry(listOf(ShopOrders, ShopOrderItems, ShopOrderTags)))
        val repository = factory.getRepository(NestedShopRepository::class.java)
        val shop = shop("Pens")
        item(order(shop, "A-1"), "Pen", 2)
        val orders = repository.findById(shop.value).get().orders
        assertEquals(listOf("A-1"), orders.map { it.number })
        assertEquals(listOf("Pen"), orders.single().items.map { it.article })
    }

    @Test
    fun testProjectionReadsCollectionStoredInRenamedEntityColumn() {
        AddressBooks.insert {
            it[name] = "Home"
            it[addressesText] = listOf(BookAddress("Berlin"), BookAddress("Paris"))
        }
        val repository = ExposedRepositoryFactory().getRepository(AddressBookRepository::class.java)
        val cities = listOf("Berlin", "Paris")
        assertEquals(cities, repository.findAll().single().addresses.map { it.city })
        assertEquals(cities, repository.findViewByName("Home").single().addresses.map { it.city })
        assertEquals(cities, repository.findSummaryByName("Home").single().addresses.map { it.city })
    }

    @Test
    fun testCollectionOfSimpleValuesIsNotARelation() {
        assertNotNull(ExposedRepositoryFactory().getRepository(SimpleCollectionRepository::class.java))
    }

    @Test
    fun testRelationForEdgeCases() {
        val mappings = ExposedMappings(tables = ExposedTableRegistry(listOf(ShopOrders, ShopOrderItems, ShopOrderTags, ShopEmployees)))
        assertTrue(mappings.relationFor(Shops, Shop::class.java, "orders") is OwnedRelation)
        assertTrue(mappings.relationFor(ShopOrders, ShopOrder::class.java, "tags") is JoinRelation)
        assertTrue(mappings.relationFor(ShopEmployees, ShopEmployee::class.java, "reports") is OwnedRelation)
        // not collections, simple elements, or a collection stored in a column of its own
        assertNull(mappings.relationFor(Shops, Shop::class.java, "name"))
        assertNull(mappings.relationFor(Shops, ShopWithAliases::class.java, "aliases"))
        assertNull(mappings.relationFor(ShopsWithOrderColumn, Shop::class.java, "orders"))
    }

    private fun creationFailure(repositoryInterface: Class<*>, vararg tables: Table): List<String> {
        val factory = ExposedRepositoryFactory(tables = ExposedTableRegistry(tables.toList()))
        val exception = assertThrows<Exception> { factory.getRepository(repositoryInterface) }
        return generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
    }

    private fun assertMentions(messages: List<String>, vararg expected: String) {
        assertTrue(messages.any { message -> expected.all { it in message } }) { "Expected a message mentioning ${expected.toList()}, got $messages" }
    }

    object Accounts : LongIdTable("inferred_accounts") {
        val name = varchar("name", 255)
    }

    /** Two foreign keys to [Accounts], so `Account.outgoing` is ambiguous. */
    object Transfers : LongIdTable("inferred_transfers") {
        val from = reference("from_id", Accounts)
        val to = reference("to_id", Accounts)
        val amount = integer("amount")
    }

    data class Transfer(val id: Long? = null, val amount: Int)

    data class Account(val id: Long? = null, val name: String, val outgoing: List<Transfer> = emptyList())

    data class AnnotatedAccount(
        val id: Long? = null,
        val name: String,
        @ExposedReferencedBy(Transfers::class, column = "from") val outgoing: List<Transfer> = emptyList(),
    )

    @ExposedRepository(table = Accounts::class)
    interface AccountRepository : ExposedCrudRepository<Account, Long>

    @ExposedRepository(table = Accounts::class)
    interface AnnotatedAccountRepository : ExposedCrudRepository<AnnotatedAccount, Long>

    object Boards : LongIdTable("inferred_boards") {
        val name = varchar("name", 255)
    }

    /** A join table with two foreign keys to [Tags], so `Board.tags` needs a target. */
    object BoardTagLinks : Table("inferred_board_tag_links") {
        val board = reference("board_id", Boards)
        val primary = reference("primary_tag_id", Tags)
        val secondary = reference("secondary_tag_id", Tags)
    }

    /** A second join table from [Boards] to [Tags], with a single target. */
    object BoardFeaturedTags : Table("inferred_board_featured_tags") {
        val board = reference("board_id", Boards)
        val tag = reference("tag_id", Tags)
    }

    data class Board(val id: Long? = null, val name: String, val tags: Set<Tag> = emptySet())

    data class PrimaryTagBoard(
        val id: Long? = null,
        val name: String,
        @ExposedReferencedBy(BoardTagLinks::class, target = "primary") val tags: Set<Tag> = emptySet(),
    )

    data class SecondaryTagBoard(
        val id: Long? = null,
        val name: String,
        @ExposedReferencedBy(BoardTagLinks::class, target = "secondary") val tags: Set<Tag> = emptySet(),
    )

    @ExposedRepository(table = Boards::class)
    interface BoardRepository : ExposedCrudRepository<Board, Long>

    @ExposedRepository(table = Boards::class)
    interface PrimaryTagBoardRepository : ExposedCrudRepository<PrimaryTagBoard, Long>

    @ExposedRepository(table = Boards::class)
    interface SecondaryTagBoardRepository : ExposedCrudRepository<SecondaryTagBoard, Long>

    @ExposedRepository(table = Shops::class)
    interface NestedShopRepository : ExposedCrudRepository<Shop, Long>

    data class ShopWithAliases(val id: Long? = null, val name: String, val aliases: List<String> = emptyList())

    /** Stores `orders` in a column of its own (e.g. serialized), so it is no relation. */
    object ShopsWithOrderColumn : LongIdTable("inferred_shops_with_order_column") {
        val name = varchar("name", 255)
        val orders = text("orders")
    }

    @ExposedRepository(table = Shops::class)
    interface SimpleCollectionRepository : ExposedCrudRepository<ShopWithAliases, Long>

    @ExposedRepository(table = Shops::class)
    interface ScalarIsEmptyShopRepository : ExposedCrudRepository<Shop, Long> {
        fun findByOrdersNumberIsEmpty(): List<Shop>
    }

    object Banks : LongIdTable("inferred_banks") {
        val name = varchar("name", 255)
    }

    object BankAccounts : LongIdTable("inferred_bank_accounts") {
        val bank = reference("bank_id", Banks)
        val name = varchar("name", 255)
    }

    /** Two foreign keys to [BankAccounts], so the hop `accounts.outgoing` is ambiguous. */
    object BankTransfers : LongIdTable("inferred_bank_transfers") {
        val from = reference("from_id", BankAccounts)
        val to = reference("to_id", BankAccounts)
        val amount = integer("amount")
    }

    data class BankTransfer(val id: Long? = null, val amount: Int)

    data class BankAccount(val id: Long? = null, val name: String, val outgoing: List<BankTransfer> = emptyList())

    data class Bank(val id: Long? = null, val name: String, val accounts: List<BankAccount> = emptyList())

    @ExposedRepository(table = Banks::class)
    interface BankRepository : ExposedCrudRepository<Bank, Long> {
        fun findByAccountsOutgoingAmount(amount: Int): List<Bank>
    }

    object Depots : LongIdTable("inferred_depots") {
        val name = varchar("name", 255)
    }

    /** Restricts deleting its depot, so the application must not delete bins on its own. */
    object DepotBins : LongIdTable("inferred_depot_bins") {
        val depot = reference("depot_id", Depots, onDelete = ReferenceOption.RESTRICT)
        val label = varchar("label", 255)
    }

    object DepotSlots : LongIdTable("inferred_depot_slots") {
        val bin = reference("bin_id", DepotBins, onDelete = ReferenceOption.RESTRICT)
        val code = varchar("code", 255)
    }

    data class DepotSlot(val id: Long? = null, val code: String)

    data class DepotBin(val id: Long? = null, val label: String, val slots: List<DepotSlot> = emptyList())

    data class Depot(val id: Long? = null, val name: String, val bins: List<DepotBin> = emptyList())

    @ExposedRepository(table = Depots::class)
    interface DepotRepository : ExposedCrudRepository<Depot, Long> {
        fun deleteByName(name: String): Long
    }

    data class BookAddress(val city: String)

    /** Stores the addresses of a book in one text column whose Kotlin name differs from the entity property. */
    object AddressBooks : LongIdTable("inferred_address_books") {
        val name = varchar("name", 255)
        val addressesText = text("addresses").transform(
            { text -> text.split(";").filter { it.isNotEmpty() }.map { BookAddress(it) } },
            { addresses -> addresses.joinToString(";") { it.city } },
        )
    }

    data class AddressBook(val id: Long? = null, val name: String, @ExposedColumn("addressesText") val addresses: List<BookAddress> = emptyList())

    /** Declares no annotation of its own: `addresses` is read from the entity's column. */
    data class AddressBookView(val name: String, val addresses: List<BookAddress>)

    interface AddressBookSummary {
        val addresses: List<BookAddress>
    }

    @ExposedRepository(table = AddressBooks::class)
    interface AddressBookRepository : ExposedCrudRepository<AddressBook, Long> {
        fun findViewByName(name: String): List<AddressBookView>

        fun findSummaryByName(name: String): List<AddressBookSummary>
    }
}

@ExposedRepository(table = Customers::class)
interface InferredCustomerRepository : ExposedCrudRepository<Customer, Long>

@ExposedRepository(table = Shops::class)
interface ShopRepository : ExposedCrudRepository<Shop, Long> {
    fun deleteByName(name: String): Long
    fun deleteAllByName(name: String): List<Shop>

    fun findByOrdersItemsArticle(article: String): List<Shop>
    fun countByOrdersItemsArticle(article: String): Long
    fun findByOrdersItemsArticleAndOrdersItemsQuantity(article: String, quantity: Int): List<Shop>
    fun findByOrdersTagsName(name: String): List<Shop>
    fun findByOrdersIsEmpty(): List<Shop>
    fun findByOrdersIsNotEmpty(): List<Shop>
    fun findByOrdersItemsIsEmpty(): List<Shop>
    fun findByOrdersItemsIsNotEmpty(): List<Shop>
    fun findByOrdersTagsIsEmpty(): List<Shop>
    fun findByOrdersTagsIsNotEmpty(): List<Shop>
    fun findByOrdersNumberAndOrdersItemsIsEmpty(number: String): List<Shop>
    fun findByOrdersIsEmptyOrName(name: String): List<Shop>
    fun findByOrdersIsNotEmptyOrderByNameDesc(): List<Shop>
    fun countByOrdersIsEmpty(): Long
    fun existsByOrdersItemsIsEmpty(): Boolean
}

@ExposedRepository(table = ShopEmployees::class)
interface ShopEmployeeRepository : ExposedCrudRepository<ShopEmployee, Long> {
    fun findByReportsIsEmpty(): List<ShopEmployee>
    fun findByReportsIsNotEmpty(): List<ShopEmployee>
    fun findByReportsReportsIsNotEmpty(): List<ShopEmployee>
}

object Shops : LongIdTable("shops") {
    val name = varchar("name", 255)
}

object ShopOrders : LongIdTable("shop_orders") {
    val shop = reference("shop_id", Shops, onDelete = ReferenceOption.CASCADE)
    val number = varchar("number", 255)
}

object ShopOrderItems : LongIdTable("shop_order_items") {
    val order = reference("order_id", ShopOrders, onDelete = ReferenceOption.CASCADE)
    val article = varchar("article", 255)
    val quantity = integer("quantity")
}

object Tags : LongIdTable("tags") {
    val name = varchar("name", 255)
}

/** A join table, `ShopOrder.tags` is many-to-many. */
object ShopOrderTags : Table("shop_order_tags") {
    val order = reference("order_id", ShopOrders, onDelete = ReferenceOption.CASCADE)
    val tag = reference("tag_id", Tags, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(order, tag)
}

/** References itself, `ShopEmployee.reports` are the employees managed by one. */
object ShopEmployees : LongIdTable("shop_employees") {
    val name = varchar("name", 255)
    val manager = optReference("manager_id", ShopEmployees, onDelete = ReferenceOption.CASCADE)
}

data class Tag(val id: Long? = null, val name: String)

data class ShopOrderItem(val id: Long? = null, val article: String, val quantity: Int)

data class ShopOrder(
    val id: Long? = null,
    val number: String,
    val items: List<ShopOrderItem> = emptyList(),
    val tags: Set<Tag> = emptySet(),
)

data class Shop(val id: Long? = null, val name: String, val orders: List<ShopOrder> = emptyList())

data class ShopEmployee(val id: Long? = null, val name: String, val manager: Long? = null, val reports: List<ShopEmployee> = emptyList())
