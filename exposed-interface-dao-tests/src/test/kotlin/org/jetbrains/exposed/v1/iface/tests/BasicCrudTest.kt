package org.jetbrains.exposed.v1.iface.tests

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.iface.tests.entities.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicCrudTest {

    private lateinit var db: Database

    @BeforeAll
    fun setup() {
        db = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    }

    @Test
    fun `test simple entity CRUD`() {
        transaction(db) {
            SchemaUtils.create(UserTable)

            // Create
            val user = UserEntity.new {
                name = "Alice"
                email = "alice@example.com"
                age = 30
            }

            assertNotNull(user.idValue)
            assertEquals("Alice", user.name)
            assertEquals("alice@example.com", user.email)
            assertEquals(30, user.age)

            // Read
            val foundUser = UserEntity.findById(user.idValue)
            assertNotNull(foundUser)
            assertEquals("Alice", foundUser.name)
            assertEquals("alice@example.com", foundUser.email)

            // Update
            foundUser.name = "Alice Smith"
            foundUser.email = "alice.smith@example.com"

            val updatedUser = UserEntity.findById(user.idValue)
            assertNotNull(updatedUser)
            assertEquals("Alice Smith", updatedUser.name)
            assertEquals("alice.smith@example.com", updatedUser.email)

            // Count
            val count = UserEntity.all().count()
            assertEquals(1, count)

            SchemaUtils.drop(UserTable)
        }
    }

    @Test
    fun `test entity with nullable fields`() {
        transaction(db) {
            SchemaUtils.create(ProfileTable)

            // Create with null values
            val profile1 = ProfileEntity.new {
                firstName = "Bob"
                lastName = "Smith"
                bio = null
                website = null
            }

            assertNotNull(profile1.idValue)
            assertEquals("Bob", profile1.firstName)
            assertNull(profile1.bio)

            // Create with non-null values
            val profile2 = ProfileEntity.new {
                firstName = "Charlie"
                lastName = "Brown"
                bio = "Software Developer"
                website = "https://example.com"
            }

            assertNotNull(profile2.bio)
            assertEquals("Software Developer", profile2.bio)
            assertNotNull(profile2.website)

            SchemaUtils.drop(ProfileTable)
        }
    }

    @Test
    fun `test entity with custom table and column names`() {
        transaction(db) {
            SchemaUtils.create(SettingTable)

            // The table should be named "app_settings"
            // Columns should be "setting_key", "setting_value"
            val setting = SettingEntity.new {
                key = "theme"
                value = "dark"
            }

            assertNotNull(setting.idValue)
            assertEquals("theme", setting.key)
            assertEquals("dark", setting.value)

            val foundSetting = SettingEntity.findById(setting.idValue)
            assertNotNull(foundSetting)
            assertEquals("theme", foundSetting.key)

            SchemaUtils.drop(SettingTable)
        }
    }

    @Test
    fun `test all() method returns all entities`() {
        transaction(db) {
            SchemaUtils.create(UserTable)

            // Create multiple users
            UserEntity.new {
                name = "User1"
                email = "user1@example.com"
                age = 25
            }

            UserEntity.new {
                name = "User2"
                email = "user2@example.com"
                age = 30
            }

            UserEntity.new {
                name = "User3"
                email = "user3@example.com"
                age = 35
            }

            val allUsers = UserEntity.all().toList()
            assertEquals(3, allUsers.count())

            val names = allUsers.map { it.name }.toSet()
            assertEquals(setOf("User1", "User2", "User3"), names)

            SchemaUtils.drop(UserTable)
        }
    }

    @Test
    fun `test data class conversion`() {
        transaction(db) {
            SchemaUtils.create(UserTable)

            val user = UserEntity.new {
                name = "David"
                email = "david@example.com"
                age = 28
            }

            // Convert to data class
            val userData = user.toData()

            assertEquals(user.idValue, userData.id)
            assertEquals(user.name, userData.name)
            assertEquals(user.email, userData.email)
            assertEquals(user.age, userData.age)

            // Verify data class is immutable (has no setters)
            // This is enforced at compile time, but we can verify the type
            assert(userData is UserData)

            SchemaUtils.drop(UserTable)
        }
    }
}
