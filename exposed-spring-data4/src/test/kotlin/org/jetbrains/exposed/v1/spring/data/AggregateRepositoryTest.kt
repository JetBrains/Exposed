package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.query.query
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.support.WindowIterator
import org.springframework.transaction.support.TransactionTemplate

/**
 * Entities and projections with nested objects: to-one references (`buyer: Buyer`), owned to-many rows (`lines`, `notes`)
 * and many-to-many rows (`labels`), how they are loaded, saved, deleted, queried and sorted by.
 */
open class AggregateRepositoryTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: AggregateCustomerRepository

    @Autowired
    lateinit var purchases: PurchaseRepository

    @Autowired
    lateinit var buyers: BuyerRepository

    @Autowired
    lateinit var staff: StaffRepository

    @BeforeEach
    fun createPurchaseTables() = TransactionTemplate(tm).executeWithoutResult {
        SchemaUtils.create(Countries, Buyers, Labels, Purchases, PurchaseLines, PurchaseNotes, PurchaseLabels)
    }

    @AfterEach
    fun dropPurchaseTables() = TransactionTemplate(tm).executeWithoutResult {
        SchemaUtils.drop(PurchaseLabels, PurchaseNotes, PurchaseLines, Purchases, Labels, Buyers, Countries)
    }

    private val germany = Country(name = "Germany")

    private fun purchase(note: String, buyer: Buyer, vararg lines: PurchaseLine) = Purchase(buyer = buyer, note = note, lines = lines.toList())

    private fun label(purchase: Purchase, name: String) {
        val label = Labels.selectAll().firstOrNull { it[Labels.name] == name }?.get(Labels.id) ?: Labels.insertAndGetId { it[Labels.name] = name }
        PurchaseLabels.insert {
            it[PurchaseLabels.purchase] = purchase.id!!
            it[PurchaseLabels.label] = label
        }
    }

    // ---- to-one: save cascades, read loads ----

    @Test
    fun testSaveInsertsNewReferencedEntitiesAndLoadsThem() {
        val saved = purchases.save(purchase("first", Buyer(name = "Ann", country = germany)))

        val buyer = saved.buyer
        assertNotNull(buyer.id)
        assertEquals("Ann", buyer.name)
        assertEquals("Germany", buyer.country?.name)
        assertNotNull(buyer.country?.id)
        assertEquals(saved, purchases.findById(saved.id!!).get())
        assertEquals(1L, buyers.count())
    }

    @Test
    fun testSaveUpdatesExistingReferencedEntities() {
        val ann = buyers.save(Buyer(name = "Ann"))
        val saved = purchases.save(purchase("first", ann.copy(name = "Anna")))

        assertEquals(ann.id, saved.buyer.id)
        assertEquals("Anna", buyers.findById(ann.id!!).get().name)
        assertEquals(1L, buyers.count())
    }

    @Test
    fun testNullableReferenceAndExplicitColumn() {
        val bob = buyers.save(Buyer(name = "Bob"))
        val withoutRecipient = purchases.save(purchase("self", bob))
        val gift = purchases.save(purchase("gift", bob).copy(recipient = Buyer(name = "Carla")))

        assertNull(withoutRecipient.recipient)
        assertEquals("Carla", gift.recipient?.name)
        // stored in Purchases.giftFor, named by @ExposedColumn
        assertEquals(gift.recipient?.id, Purchases.selectAll().single { it[Purchases.note] == "gift" }[Purchases.giftFor]?.value)
    }

    @Test
    fun testReferencedRowsAreLoadedWithOneQueryForAllRows() {
        val ann = buyers.save(Buyer(name = "Ann", country = germany))
        val bob = buyers.save(Buyer(name = "Bob"))
        purchases.saveAll(listOf(purchase("a", ann), purchase("b", bob), purchase("c", ann)))

        val selects = captureSelects { assertEquals(3, purchases.findAll().size) }
        // purchases, buyers, countries, lines, notes, labels (links)
        assertEquals(6, selects.size) { selects.joinToString("\n") }
    }

    // ---- projections decide what is loaded ----

    @Test
    fun testProjectionWithKeyDoesNotLoadTheReference() {
        val saved = purchases.save(purchase("first", Buyer(name = "Ann")))

        val selects = captureSelects { assertEquals(PurchaseSummary("first", saved.buyer.id!!), purchases.findSummaryByNote("first")) }
        assertEquals(1, selects.size) { selects.joinToString("\n") }
    }

    @Test
    fun testDtoAndInterfaceProjectionsWithNestedProjections() {
        purchases.save(purchase("first", Buyer(name = "Ann", country = germany), PurchaseLine(article = "Pen", quantity = 1)))

        assertEquals(PurchaseWithBuyerName("first", BuyerName("Ann")), purchases.findWithBuyerNameByNote("first"))

        val view = checkNotNull(purchases.findViewByNote("first"))
        assertEquals("first", view.note)
        assertEquals("Ann", view.buyer.name)
        assertEquals(listOf("Pen"), view.lines.map { it.article })
    }

    @Test
    fun testExplicitColumnOfPlainProperty() {
        purchases.save(purchase("first", Buyer(name = "Ann")))
        assertEquals(PurchaseWithBuyerAlias("first", BuyerAlias("Ann")), purchases.findWithBuyerAliasByNote("first"))
    }

    @Test
    fun testSelfReferencingType() {
        SchemaUtils.create(StaffMembers)
        val boss = StaffMember(name = "Boss")
        val saved = staff.save(StaffMember(name = "Dev", manager = StaffMember(name = "Lead", manager = boss)))

        assertEquals("Lead", saved.manager?.name)
        assertEquals("Boss", saved.manager?.manager?.name)
        assertNull(saved.manager?.manager?.manager)
        assertEquals(3L, staff.count())
        SchemaUtils.drop(StaffMembers)
    }

    @Test
    fun testQueryHelperLoadsNestedObjects() {
        purchases.save(purchase("first", Buyer(name = "Ann"), PurchaseLine(article = "Pen", quantity = 1)))
        val loaded = purchases.allWithBuyerName()
        assertEquals(listOf(PurchaseWithBuyerName("first", BuyerName("Ann"))), loaded)
    }

    // ---- owned to-many rows ----

    @Test
    fun testSaveInsertsUpdatesAndDeletesOwnedRowsByKey() {
        val saved = purchases.save(
            purchase("first", Buyer(name = "Ann"), PurchaseLine(article = "Pen", quantity = 1), PurchaseLine(article = "Pad", quantity = 2))
        )
        val (pen, pad) = saved.lines
        assertNotNull(pen.id)

        val changed = purchases.save(saved.copy(lines = listOf(pen.copy(quantity = 3), PurchaseLine(article = "Ink", quantity = 1))))

        assertEquals(listOf("Pen" to 3, "Ink" to 1), changed.lines.map { it.article to it.quantity })
        // the kept row is updated, not deleted and inserted again
        assertEquals(pen.id, changed.lines.first().id)
        assertFalse(PurchaseLines.selectAll().any { it[PurchaseLines.id].value == pad.id })
    }

    @Test
    fun testSaveMatchesOwnedRowsWithoutKeyByValue() {
        val saved = purchases.save(
            purchase("first", Buyer(name = "Ann")).copy(notes = listOf(PurchaseNote("fast"), PurchaseNote("fast"), PurchaseNote("cheap")))
        )
        assertEquals(listOf("fast", "fast", "cheap").sorted(), saved.notes.map { it.text }.sorted())

        val changed = purchases.save(saved.copy(notes = listOf(PurchaseNote("fast"), PurchaseNote("cheap"), PurchaseNote("new"))))

        assertEquals(listOf("cheap", "fast", "new"), changed.notes.map { it.text }.sorted())
        assertEquals(3, PurchaseNotes.selectAll().count())
    }

    @Test
    fun testDeleteDeletesOwnedRows() {
        val first = purchases.save(purchase("first", Buyer(name = "Ann"), PurchaseLine(article = "Pen", quantity = 1)).copy(notes = listOf(PurchaseNote("x"))))
        val second = purchases.save(purchase("second", first.buyer, PurchaseLine(article = "Pad", quantity = 1)))
        val third = purchases.save(purchase("third", first.buyer, PurchaseLine(article = "Ink", quantity = 1)))

        purchases.delete(first)
        assertEquals(listOf("Pad", "Ink"), PurchaseLines.selectAll().map { it[PurchaseLines.article] })
        assertEquals(0, PurchaseNotes.selectAll().count())

        purchases.deleteById(second.id!!)
        assertEquals(1L, purchases.deleteByNote(third.note))
        assertEquals(0, PurchaseLines.selectAll().count())
        // referenced entities are not owned
        assertEquals(1L, buyers.count())

        purchases.save(purchase("fourth", first.buyer, PurchaseLine(article = "Pen", quantity = 1)))
        purchases.deleteAll()
        assertEquals(0, PurchaseLines.selectAll().count())
    }

    // ---- many-to-many rows are only read ----

    @Test
    fun testManyToManyIsLoadedButNotWritten() {
        val first = purchases.save(purchase("first", Buyer(name = "Ann")))
        val second = purchases.save(purchase("second", first.buyer))
        label(first, "urgent")
        label(first, "gift")
        label(second, "urgent")

        val loaded = purchases.findById(first.id!!).get()
        assertEquals(setOf("urgent", "gift"), loaded.labels.map { it.name }.toSet())

        purchases.save(loaded.copy(labels = emptySet()))
        assertEquals(3, PurchaseLabels.selectAll().count())
        assertEquals(listOf(first.id!!, second.id!!), purchases.findByLabelsName("urgent").map { it.id!! }.sorted())
    }

    // ---- derived queries ----

    @Test
    fun testFindByToOneProperties() {
        val ann = buyers.save(Buyer(name = "Ann", country = germany))
        val bob = buyers.save(Buyer(name = "Bob"))
        val first = purchases.save(purchase("first", ann))
        val second = purchases.save(purchase("second", bob).copy(recipient = ann))

        assertEquals(listOf(first.id), purchases.findByBuyerName("Ann").map { it.id })
        assertEquals(listOf(first.id), purchases.findByBuyerCountryName("Germany").map { it.id })
        assertEquals(listOf(second.id), purchases.findByBuyerId(bob.id!!).map { it.id })
        assertEquals(listOf(second.id), purchases.findByBuyer(bob).map { it.id })
        assertEquals(listOf(second.id), purchases.findByRecipientName("Ann").map { it.id })
    }

    @Test
    fun testReferencedIdIsComparedWithoutSubquery() {
        val bob = buyers.save(Buyer(name = "Bob"))
        purchases.save(purchase("first", bob))
        val sql = captureSelects { purchases.findByBuyerId(bob.id!!) }.first().uppercase()
        assertFalse("IN (SELECT" in sql) { sql }
    }

    @Test
    fun testAndPartsOnTheSameToManyPathMatchTheSameRow() {
        val ann = buyers.save(Buyer(name = "Ann"))
        val both = purchases.save(purchase("both", ann, PurchaseLine(article = "Pen", quantity = 2)))
        // has a Pen and a line with quantity 2, but not one line with both
        purchases.save(purchase("split", ann, PurchaseLine(article = "Pen", quantity = 1), PurchaseLine(article = "Pad", quantity = 2)))

        assertEquals(listOf(both.id), purchases.findByLinesArticleAndLinesQuantity("Pen", 2).map { it.id })
        val sql = captureSelects { purchases.findByLinesArticleAndLinesQuantity("Pen", 2) }.first().uppercase()
        assertEquals(1, Regex("IN \\(SELECT").findAll(sql).count()) { sql }

        // Or parts stay separate
        assertEquals(2, purchases.findByLinesArticleOrLinesQuantity("Pen", 2).size)
    }

    // ---- sorting by to-one paths ----

    @Test
    fun testSortByReferencedProperty() {
        val carla = buyers.save(Buyer(name = "Carla"))
        val ann = buyers.save(Buyer(name = "Ann"))
        val bob = buyers.save(Buyer(name = "Bob"))
        purchases.saveAll(listOf(purchase("p1", carla), purchase("p2", ann), purchase("p3", bob)))

        assertEquals(listOf("p2", "p3", "p1"), purchases.findByNoteStartingWithOrderByBuyerNameAsc("p").map { it.note })
        assertEquals(listOf("p1", "p3", "p2"), purchases.findAll(Sort.by(Sort.Order.desc("buyer.name"))).map { it.note })

        val page = purchases.findAll(PageRequest.of(1, 2, Sort.by("buyer.name")))
        assertEquals(listOf("p1"), page.content.map { it.note })
        assertEquals(3L, page.totalElements)
    }

    @Test
    fun testKeysetScrollByReferencedProperty() {
        val names = listOf("Dan", "Ann", "Carla", "Bob")
        names.forEachIndexed { i, name -> purchases.save(purchase("p$i", Buyer(name = name))) }

        val scrolled = WindowIterator.of<Purchase> { purchases.findFirst2ByNoteStartingWith("p", it, Sort.by("buyer.name")) }
            .startingAt(ScrollPosition.keyset())
            .asSequence()
            .map { it.buyer.name }
            .toList()
        assertEquals(names.sorted(), scrolled)
    }

    @Test
    fun testSortByToManyPathIsRejected() {
        purchases.save(purchase("first", Buyer(name = "Ann")))
        assertThrows<Exception> { purchases.findAll(Sort.by("lines.article")) }
    }

    @Test
    fun testOrderByToManyPathFailsAtCreation() {
        val exception = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(OrderByToManyRepository::class.java) }
        val messages = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { "to-many" in it }) { messages.toString() }
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Purchases::class)
    interface OrderByToManyRepository : ExposedCrudRepository<Purchase, Long> {
        fun findByNoteOrderByLinesArticleAsc(note: String): List<Purchase>
    }
}

@ExposedRepository(table = Customers::class)
interface AggregateCustomerRepository : ExposedCrudRepository<Customer, Long>

@ExposedRepository(table = Buyers::class)
interface BuyerRepository : ExposedCrudRepository<Buyer, Long>

@ExposedRepository(table = StaffMembers::class)
interface StaffRepository : ExposedCrudRepository<StaffMember, Long>

object StaffMembers : LongIdTable("staff_members") {
    val name = varchar("name", 255)
    val manager = optReference("manager_id", StaffMembers)
}

data class StaffMember(val id: Long? = null, val name: String, val manager: StaffMember? = null)

@ExposedRepository(table = Purchases::class)
interface PurchaseRepository : ExposedCrudRepository<Purchase, Long> {
    fun findByBuyerName(name: String): List<Purchase>
    fun findByBuyerCountryName(name: String): List<Purchase>
    fun findByBuyerId(id: Long): List<Purchase>
    fun findByBuyer(buyer: Buyer): List<Purchase>
    fun findByRecipientName(name: String): List<Purchase>
    fun findByLinesArticleAndLinesQuantity(article: String, quantity: Int): List<Purchase>
    fun findByLinesArticleOrLinesQuantity(article: String, quantity: Int): List<Purchase>
    fun findByLabelsName(name: String): List<Purchase>
    fun findByNoteStartingWithOrderByBuyerNameAsc(prefix: String): List<Purchase>
    fun findFirst2ByNoteStartingWith(prefix: String, position: ScrollPosition, sort: Sort): Window<Purchase>
    fun findSummaryByNote(note: String): PurchaseSummary?
    fun findWithBuyerNameByNote(note: String): PurchaseWithBuyerName?
    fun findViewByNote(note: String): PurchaseView?
    fun findWithBuyerAliasByNote(note: String): PurchaseWithBuyerAlias?
    fun deleteByNote(note: String): Long

    fun allWithBuyerName(): List<PurchaseWithBuyerName> = query { Purchases.selectAll().toList() }
}

object Countries : LongIdTable("countries") {
    val name = varchar("name", 255)
}

object Buyers : LongIdTable("buyers") {
    val name = varchar("name", 255)
    val country = optReference("country_id", Countries)
}

object Labels : LongIdTable("labels") {
    val name = varchar("name", 255)
}

object Purchases : LongIdTable("purchases") {
    val buyer = reference("buyer_id", Buyers)
    val giftFor = optReference("gift_for_id", Buyers)
    val note = varchar("note", 255)
}

/** Owned rows with a primary key, matched by it. */
object PurchaseLines : LongIdTable("purchase_lines") {
    val purchase = reference("purchase_id", Purchases)
    val article = varchar("article", 255)
    val quantity = integer("quantity")
}

/** Owned rows without a primary key, matched by their values. */
object PurchaseNotes : Table("purchase_notes") {
    val purchase = reference("purchase_id", Purchases)
    val text = varchar("text", 255)
}

/** A join table, [Purchase.labels] is many-to-many. */
object PurchaseLabels : Table("purchase_labels") {
    val purchase = reference("purchase_id", Purchases)
    val label = reference("label_id", Labels)
    override val primaryKey = PrimaryKey(purchase, label)
}

data class Country(val id: Long? = null, val name: String)

data class Buyer(val id: Long? = null, val name: String, val country: Country? = null)

data class Label(val id: Long? = null, val name: String)

data class PurchaseLine(val id: Long? = null, val article: String, val quantity: Int)

data class PurchaseNote(val text: String)

data class Purchase(
    val id: Long? = null,
    val buyer: Buyer,
    @ExposedColumn("giftFor") val recipient: Buyer? = null,
    val note: String,
    @ExposedReferencedBy(PurchaseLines::class) val lines: List<PurchaseLine> = emptyList(),
    @ExposedReferencedBy(PurchaseNotes::class) val notes: List<PurchaseNote> = emptyList(),
    @ExposedReferencedBy(PurchaseLabels::class, target = "label") val labels: Set<Label> = emptySet(),
)

/** Has the key of the buyer, so the buyer isn't loaded. */
data class PurchaseSummary(val note: String, val buyer: Long)

data class BuyerName(val name: String)

data class PurchaseWithBuyerName(val note: String, val buyer: BuyerName)

data class BuyerAlias(@ExposedColumn("name") val displayName: String)

data class PurchaseWithBuyerAlias(val note: String, @ExposedColumn("buyer") val purchaser: BuyerAlias)

interface BuyerView {
    val name: String
}

interface LineView {
    val article: String
}

interface PurchaseView {
    val note: String
    val buyer: BuyerView
    val lines: List<LineView>
}
