package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.data.AggregateRepositoryTest.OrderByToManyRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.AccountRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.BankAccounts
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.BankRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.BankTransfers
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.BoardRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.BoardTagLinks
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.NestedShopRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.ScalarIsEmptyShopRepository
import org.jetbrains.exposed.v1.spring.data.InferredRelationRepositoryTest.Transfers
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedTableRegistry
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.transaction.support.TransactionTemplate

/**
 * The exact H2 SQL and error messages quoted in `SPRING_DATA_EXPOSED_SEMANTICS.md`, with the fixtures of
 * [InferredRelationRepositoryTest], [AggregateRepositoryTest] and [DerivedQueryJoinTest].
 *
 * Each test asserts the full text and prints it as `DOC <label>: <text>`, so the JUnit XML report holds the strings
 * that the document copies. If a test fails here, update the document with the new text.
 */
open class SemanticsDocumentExamplesTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: InferredCustomerRepository

    @Autowired
    lateinit var shops: ShopRepository

    @Autowired
    lateinit var employees: ShopEmployeeRepository

    @Autowired
    lateinit var purchases: PurchaseRepository

    @Autowired
    lateinit var orders: OrderRepository

    private val tables = arrayOf(
        Shops, ShopOrders, ShopOrderItems, Tags, ShopOrderTags, ShopEmployees,
        Countries, Buyers, Labels, Purchases, PurchaseLines, PurchaseNotes, PurchaseLabels,
        Products, Orders, OrderItems, OrderComments,
    )

    @BeforeEach
    fun createDocumentTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(*tables) }

    @AfterEach
    fun dropDocumentTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(*tables.reversedArray()) }

    private fun assertDoc(label: String, expected: String, actual: String) {
        println("DOC $label: $actual")
        assertEquals(expected, actual) { label }
    }

    /** The SQL of the query itself: the first select, before the selects that load the relations of the result. */
    private fun query(block: () -> Unit): String = captureSelects(block).first()

    // ---- section 3: reading ----

    @Test
    fun testLoadSql() {
        val shop = shops.save(Shop(name = "Pens", orders = listOf(ShopOrder(number = "A-1", items = listOf(ShopOrderItem(article = "Pen", quantity = 1))))))
        val selects = captureSelects { shops.findById(shop.id!!) }
        assertDoc(
            "load",
            listOf(
                "SELECT SHOPS.ID, SHOPS.\"name\" FROM SHOPS WHERE SHOPS.ID = ?",
                "SELECT SHOP_ORDERS.ID, SHOP_ORDERS.\"number\", SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS WHERE SHOP_ORDERS.SHOP_ID = ? ORDER BY SHOP_ORDERS.ID ASC",
                "SELECT SHOP_ORDER_ITEMS.ID, SHOP_ORDER_ITEMS.ARTICLE, SHOP_ORDER_ITEMS.QUANTITY, SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS " +
                    "WHERE SHOP_ORDER_ITEMS.ORDER_ID = ? ORDER BY SHOP_ORDER_ITEMS.ID ASC",
                "SELECT SHOP_ORDER_TAGS.ORDER_ID, SHOP_ORDER_TAGS.TAG_ID FROM SHOP_ORDER_TAGS WHERE SHOP_ORDER_TAGS.ORDER_ID = ? " +
                    "ORDER BY SHOP_ORDER_TAGS.ORDER_ID ASC, SHOP_ORDER_TAGS.TAG_ID ASC",
            ).joinToString("\n"),
            selects.joinToString("\n"),
        )
    }

    // ---- sections 4 and 7.8: saving and deleting ----

    @Test
    fun testOrphanRemovalAndDeleteSql() {
        val shop = shops.save(Shop(name = "Pens", orders = listOf(ShopOrder(number = "A-1"))))
        assertDoc("orphan removal", "DELETE FROM SHOP_ORDERS WHERE SHOP_ORDERS.ID = ?", captureDeletes { shops.save(shop.copy(orders = emptyList())) }.single())
        assertDoc("deleteById", "DELETE FROM SHOPS WHERE SHOPS.ID = ?", captureDeletes { shops.deleteById(shop.id!!) }.single())
        assertDoc("deleteByName", "DELETE FROM SHOPS WHERE SHOPS.\"name\" = ?", captureDeletes { shops.deleteByName("Pens") }.single())
    }

    // ---- sections 7.3, 7.9 and 7.10: derived queries through inferred relations ----

    @Test
    fun testDeepQuerySql() {
        val select = "SELECT SHOPS.ID, SHOPS.\"name\" FROM SHOPS WHERE "
        assertDoc(
            "findByOrdersItemsArticle",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS " +
                "WHERE SHOP_ORDERS.ID IN (SELECT SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS WHERE SHOP_ORDER_ITEMS.ARTICLE = ?))",
            query { shops.findByOrdersItemsArticle("Pen") },
        )
        assertDoc(
            "findByOrdersItemsArticleAndOrdersItemsQuantity",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS " +
                "WHERE SHOP_ORDERS.ID IN (SELECT SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS " +
                "WHERE (SHOP_ORDER_ITEMS.ARTICLE = ?) AND (SHOP_ORDER_ITEMS.QUANTITY = ?)))",
            query { shops.findByOrdersItemsArticleAndOrdersItemsQuantity("Pen", 1) },
        )
        assertDoc(
            "findByOrdersTagsName",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS " +
                "WHERE SHOP_ORDERS.ID IN (SELECT SHOP_ORDER_TAGS.ORDER_ID FROM SHOP_ORDER_TAGS " +
                "WHERE SHOP_ORDER_TAGS.TAG_ID IN (SELECT TAGS.ID FROM TAGS WHERE TAGS.\"name\" = ?)))",
            query { shops.findByOrdersTagsName("urgent") },
        )
    }

    @Test
    fun testEmptinessSql() {
        val select = "SELECT SHOPS.ID, SHOPS.\"name\" FROM SHOPS WHERE "
        val noOrders = "SHOPS.ID NOT IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS WHERE SHOP_ORDERS.SHOP_ID IS NOT NULL)"
        assertDoc("findByOrdersIsEmpty", select + noOrders, query { shops.findByOrdersIsEmpty() })
        assertDoc(
            "findByOrdersItemsIsNotEmpty",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS WHERE SHOP_ORDERS.ID IN (SELECT SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS))",
            query { shops.findByOrdersItemsIsNotEmpty() },
        )
        assertDoc(
            "findByOrdersTagsIsEmpty",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS " +
                "WHERE SHOP_ORDERS.ID NOT IN (SELECT SHOP_ORDER_TAGS.ORDER_ID FROM SHOP_ORDER_TAGS WHERE SHOP_ORDER_TAGS.ORDER_ID IS NOT NULL))",
            query { shops.findByOrdersTagsIsEmpty() },
        )
        assertDoc(
            "findByOrdersNumberAndOrdersItemsIsEmpty",
            select + "SHOPS.ID IN (SELECT SHOP_ORDERS.SHOP_ID FROM SHOP_ORDERS WHERE (SHOP_ORDERS.\"number\" = ?) AND " +
                "(SHOP_ORDERS.ID NOT IN (SELECT SHOP_ORDER_ITEMS.ORDER_ID FROM SHOP_ORDER_ITEMS WHERE SHOP_ORDER_ITEMS.ORDER_ID IS NOT NULL)))",
            query { shops.findByOrdersNumberAndOrdersItemsIsEmpty("C-1") },
        )
        assertDoc("findByOrdersIsEmptyOrName", "$select($noOrders) OR (SHOPS.\"name\" = ?)", query { shops.findByOrdersIsEmptyOrName("Pads") })
        assertDoc(
            "findByReportsIsEmpty",
            "SELECT SHOP_EMPLOYEES.ID, SHOP_EMPLOYEES.\"name\", SHOP_EMPLOYEES.MANAGER_ID FROM SHOP_EMPLOYEES " +
                "WHERE SHOP_EMPLOYEES.ID NOT IN (SELECT SHOP_EMPLOYEES.MANAGER_ID FROM SHOP_EMPLOYEES WHERE SHOP_EMPLOYEES.MANAGER_ID IS NOT NULL)",
            query { employees.findByReportsIsEmpty() },
        )
    }

    // ---- sections 7.6 and 7.9: annotated relations, to-one hops, sorting ----

    @Test
    fun testOrToOneAndSortSql() {
        val select = "SELECT PURCHASES.ID, PURCHASES.BUYER_ID, PURCHASES.GIFT_FOR_ID, PURCHASES.NOTE FROM PURCHASES "
        assertDoc(
            "findByLinesArticleOrLinesQuantity",
            select + "WHERE (PURCHASES.ID IN (SELECT PURCHASE_LINES.PURCHASE_ID FROM PURCHASE_LINES WHERE PURCHASE_LINES.ARTICLE = ?)) " +
                "OR (PURCHASES.ID IN (SELECT PURCHASE_LINES.PURCHASE_ID FROM PURCHASE_LINES WHERE PURCHASE_LINES.QUANTITY = ?))",
            query { purchases.findByLinesArticleOrLinesQuantity("Pen", 2) },
        )
        assertDoc(
            "findByBuyerCountryName",
            select + "WHERE PURCHASES.BUYER_ID IN (SELECT BUYERS.ID FROM BUYERS " +
                "WHERE BUYERS.COUNTRY_ID IN (SELECT COUNTRIES.ID FROM COUNTRIES WHERE COUNTRIES.\"name\" = ?))",
            query { purchases.findByBuyerCountryName("Germany") },
        )
        assertDoc("findByBuyerId", select + "WHERE PURCHASES.BUYER_ID = ?", query { purchases.findByBuyerId(1L) })
        assertDoc(
            "findByNoteStartingWithOrderByBuyerNameAsc",
            select + "LEFT JOIN BUYERS sort_0 ON PURCHASES.BUYER_ID = sort_0.ID WHERE PURCHASES.NOTE LIKE ? ESCAPE ? ORDER BY sort_0.\"name\" ASC",
            query { purchases.findByNoteStartingWithOrderByBuyerNameAsc("p") },
        )
        assertDoc(
            "findByCommentsText",
            "SELECT ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.NOTE FROM ORDERS " +
                "WHERE ORDERS.ID IN (SELECT ORDER_COMMENTS.ORDER_ID FROM ORDER_COMMENTS WHERE ORDER_COMMENTS.TEXT = ?)",
            query { orders.findByCommentsText("slow") },
        )
    }

    // ---- sections 2.3, 6.3 and 6.4: error messages ----

    private fun creationFailure(repositoryInterface: Class<*>, vararg tables: Table): List<String> {
        val factory = ExposedRepositoryFactory(tables = ExposedTableRegistry(tables.toList()))
        val exception = assertThrows<Exception> { factory.getRepository(repositoryInterface) }
        return generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
    }

    private fun assertDocMessage(label: String, expected: String, messages: List<String>) {
        messages.forEach { println("DOC $label: $it") }
        assertTrue(expected in messages) { "Expected the message\n$expected\nin\n${messages.joinToString("\n")}" }
    }

    @Test
    fun testRelationErrorMessages() {
        assertDocMessage(
            "not mapped",
            "Shop.orders is not mapped: no known table has a foreign key to shops whose rows, or the rows they reference, " +
                "have a column for every property of ShopOrder. Annotate 'orders' with @ExposedReferencedBy(<Table>::class), " +
                "or make sure the table is in a scanned package",
            creationFailure(NestedShopRepository::class.java),
        )
        assertDocMessage(
            "ambiguous column",
            "Account.outgoing is ambiguous, these relations to inferred_accounts fit: Transfers.from (one-to-many), Transfers.to (one-to-many). " +
                "Annotate it with @ExposedReferencedBy(Transfers::class, column = \"...\") to choose one (set the column)",
            creationFailure(AccountRepository::class.java, Transfers),
        )
        assertDocMessage(
            "ambiguous target",
            "Board.tags is ambiguous, these relations to inferred_boards fit: " +
                "BoardTagLinks.board with target BoardTagLinks.primary (many-to-many to tags), " +
                "BoardTagLinks.board with target BoardTagLinks.secondary (many-to-many to tags). " +
                "Annotate it with @ExposedReferencedBy(BoardTagLinks::class, target = \"...\") to choose one (set the target)",
            creationFailure(BoardRepository::class.java, BoardTagLinks),
        )
        assertDocMessage(
            "ambiguous deep",
            "BankAccount.outgoing is ambiguous, these relations to inferred_bank_accounts fit: BankTransfers.from (one-to-many), BankTransfers.to (one-to-many). " +
                "Annotate it with @ExposedReferencedBy(BankTransfers::class, column = \"...\") to choose one (set the column)",
            creationFailure(BankRepository::class.java, BankAccounts, BankTransfers),
        )
    }

    @Test
    fun testQueryErrorMessages() {
        assertDocMessage(
            "emptiness",
            "'number' of the path 'orders.number' is no to-many relation (a collection, inferred or annotated with @ExposedReferencedBy), " +
                "so it can't be checked for emptiness",
            creationFailure(ScalarIsEmptyShopRepository::class.java, ShopOrders, ShopOrderItems, ShopOrderTags),
        )
        val sortMessage = "Sorting by 'lines.article' is not supported, the path goes through a to-many property"
        assertDocMessage("sort in method name", sortMessage, creationFailure(OrderByToManyRepository::class.java, PurchaseLines, PurchaseNotes, PurchaseLabels))
        val sortParameter = assertThrows<IllegalArgumentException> { purchases.findAll(Sort.by("lines.article")) }
        assertDocMessage("sort parameter", sortMessage, listOf(sortParameter.message!!))
    }
}
