package org.jetbrains.exposed.v1.iface.tests

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.iface.tests.entities.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConstraintsAndDefaultsTest {

    private lateinit var db: Database

    @BeforeAll
    fun setup() {
        db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    }

    @Test
    fun `test unique constraint prevents duplicates`() {
        transaction(db) {
            SchemaUtils.create(AccountTable)

            // Create first account
            AccountEntity.new {
                username = "uniqueuser"
                email = "unique@example.com"
                displayName = "Unique User"
                isActive = true
            }

            // Try to create second account with same username (should fail)
            assertFailsWith<Exception> {
                val duplicate = AccountEntity.new {
                    username = "uniqueuser"  // Duplicate!
                    email = "another@example.com"
                    displayName = "Another User"
                    isActive = true
                }
                duplicate.idValue  // Force flush to trigger constraint check
            }

            SchemaUtils.drop(AccountTable)
        }
    }

    @Test
    fun `test unique constraint on setting key`() {
        transaction(db) {
            SchemaUtils.create(SettingTable)

            // Create first setting
            SettingEntity.new {
                key = "api_key"
                value = "secret123"
            }

            // Try to create second setting with same key (should fail)
            assertFailsWith<Exception> {
                val duplicate = SettingEntity.new {
                    key = "api_key"  // Duplicate!
                    value = "secret456"
                }
                duplicate.idValue  // Force flush to trigger constraint check
            }

            SchemaUtils.drop(SettingTable)
        }
    }

//     @Test
//     fun `test CurrentTimestamp default sets creation time`() {
//         transaction(db) {
//             SchemaUtils.create(PostTable)
//
//             val beforeCreation = Instant.now()
//
//             // Sleep a tiny bit to ensure time difference
//             Thread.sleep(10)
//
//             val post = PostEntity.new {
//                 title = "Test Post"
//                 content = "Test content"
//                 viewCount = 0
//                 isPublished = false
//             }
//
//             Thread.sleep(10)
//             val afterCreation = Instant.now()
//
//             assertNotNull(post)
//             assertTrue(post.isAfter(beforeCreation) || post == beforeCreation)
//             assertTrue(post.isBefore(afterCreation) || post == afterCreation)
//
//             SchemaUtils.drop(PostTable)
//         }
//     }

    @Test
    fun `test ClientDefault for integer`() {
        transaction(db) {
            SchemaUtils.create(PostTable)

            // viewCount has @ClientDefault("0")
            val post = PostEntity.new {
                title = "Test Post"
                content = "Content"
                isPublished = false
                // viewCount not set, should default to 0
            }

            assertEquals(0, post.viewCount)

            SchemaUtils.drop(PostTable)
        }
    }

    @Test
    fun `test ClientDefault for boolean`() {
        transaction(db) {
            SchemaUtils.create(PostTable)

            // isPublished has @ClientDefault("false")
            val post = PostEntity.new {
                title = "Test Post"
                content = "Content"
                viewCount = 5
                // isPublished not set, should default to false
            }

            assertEquals(false, post.isPublished)

            SchemaUtils.drop(PostTable)
        }
    }

    @Test
    fun `test client default can be overridden`() {
        transaction(db) {
            SchemaUtils.create(PostTable)

            val post = PostEntity.new {
                title = "Published Post"
                content = "Content"
                viewCount = 100  // Override default of 0
                isPublished = true  // Override default of false
            }

            assertEquals(100, post.viewCount)
            assertEquals(true, post.isPublished)

            SchemaUtils.drop(PostTable)
        }
    }

    @Test
    fun `test index allows duplicates but is queryable`() {
        transaction(db) {
            SchemaUtils.create(AccountTable)

            // displayName has @Index (not unique)
            // Multiple accounts can have same display name

            AccountEntity.new {
                username = "user1"
                email = "user1@example.com"
                displayName = "John Doe"  // Same display name
                isActive = true
            }

            AccountEntity.new {
                username = "user2"
                email = "user2@example.com"
                displayName = "John Doe"  // Same display name (OK, just indexed)
                isActive = true
            }

            // Both should exist
            assertEquals(2, AccountEntity.all().count())

            SchemaUtils.drop(AccountTable)
        }
    }
//
//     @Test
//     fun `test CurrentTimestamp default on setting`() {
//         transaction(db) {
//             SchemaUtils.create(SettingTable)
//
//             val before = Instant.now()
//             Thread.sleep(10)
//
//             val setting = SettingEntity.new {
//                 key = "test_key"
//                 value = "test_value"
//             }
//
//             Thread.sleep(10)
//             val after = Instant.now()
//
//             assertNotNull(setting)
//             assertTrue(setting.isAfter(before) || setting == before)
//             assertTrue(setting.isBefore(after) || setting == after)
//
//             SchemaUtils.drop(SettingTable)
//         }
//     }
}
