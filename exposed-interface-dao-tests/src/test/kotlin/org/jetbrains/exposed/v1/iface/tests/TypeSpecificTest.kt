package org.jetbrains.exposed.v1.iface.tests

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.iface.tests.entities.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeSpecificTest {

    private lateinit var db: Database

    @BeforeAll
    fun setup() {
        db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    }

    @Test
    fun `test varchar with custom length`() {
        transaction(db) {
            SchemaUtils.create(ProductTable)

            // ProductTable.name should have varchar(100)
            // ProductTable.description should be text (unlimited)

            val product = ProductEntity.new {
                name = "Product A"
                description = "A".repeat(1000) // Long description
                price = BigDecimal("99.99")
                stock = 10
            }

            assertNotNull(product.idValue)
            assertEquals("Product A", product.name)
            assertTrue(product.description.length == 1000)

            SchemaUtils.drop(ProductTable)
        }
    }

    @Test
    fun `test decimal with precision and scale`() {
        transaction(db) {
            SchemaUtils.create(ProductTable)

            // ProductTable.price should be decimal(10, 2)

            val product1 = ProductEntity.new {
                name = "Product B"
                description = "Test product"
                price = BigDecimal("12345678.91")  // 10 digits total, 2 after decimal
                stock = 5
            }

            assertEquals(BigDecimal("12345678.91"), product1.price)

            val product2 = ProductEntity.new {
                name = "Product C"
                description = "Another product"
                price = BigDecimal("0.99")
                stock = 100
            }

            assertEquals(BigDecimal("0.99"), product2.price)

            SchemaUtils.drop(ProductTable)
        }
    }

    @Test
    fun `test text column for long content`() {
        transaction(db) {
            SchemaUtils.create(PostTable)

            // PostTable.content should be text (unlimited)
            val longContent = "Lorem ipsum ".repeat(1000) // Very long content

            val post = PostEntity.new {
                title = "Long Post"
                content = longContent
                viewCount = 0
                isPublished = false
            }

            assertNotNull(post.idValue)
            assertEquals(longContent, post.content)

            SchemaUtils.drop(PostTable)
        }
    }

    @Test
    fun `test account with different varchar lengths`() {
        transaction(db) {
            SchemaUtils.create(AccountTable)

            // username: varchar(50)
            // email: varchar(255)
            // displayName: varchar(100)

            val account = AccountEntity.new {
                username = "john_doe_12345"  // 50 chars max
                email = "john.doe@example.com"  // 255 chars max
                displayName = "John Doe"  // 100 chars max
                isActive = true
            }

            assertNotNull(account.idValue)
            assertEquals("john_doe_12345", account.username)
            assertEquals("john.doe@example.com", account.email)
            assertEquals("John Doe", account.displayName)
            assertTrue(account.isActive)

            SchemaUtils.drop(AccountTable)
        }
    }
}
