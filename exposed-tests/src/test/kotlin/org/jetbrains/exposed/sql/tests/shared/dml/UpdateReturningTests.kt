package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.crypt.Algorithms
import org.jetbrains.exposed.crypt.encryptedBinary
import org.jetbrains.exposed.crypt.encryptedVarchar
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import java.lang.IllegalArgumentException

class UpdateReturningTests : DatabaseTestsBase() {
    private val notSupportLimit by lazy {
        val exclude = arrayListOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.H2_PSQL)
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }
    private val notSupportReturning by lazy {
        val exclude = arrayListOf(TestDB.H2, TestDB.MARIADB, TestDB.SQLSERVER, TestDB.MYSQL, TestDB.H2_MYSQL)
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }

    @Test
    fun testUpdateReturning01() {
        withCitiesAndUsers(exclude = notSupportReturning) { _, users, _ ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select { users.id.eq(alexId) }.first()[users.name]
            assertEquals("Alex", alexName)

            val newName = "Alexey"
            val alexNewName = users.updateReturning({ users.id.eq(alexId) }, null, users) {
                it[users.name] = newName
            }.asSequence().toList().first()[users.name]

            assertEquals(newName, alexNewName)
        }
    }

    @Test
    fun testUpdateWithLimit01() {
        withCitiesAndUsers(exclude = notSupportLimit + notSupportReturning) { _, users, _ ->
            val aNames = users.slice(users.name).select { users.id like "a%" }.map { it[users.name] }
            assertEquals(2, aNames.size)

            users.updateReturning({ users.id like "a%" }, 1, users) {
                it[users.id] = "NewName"
            }

            val unchanged = users.slice(users.name).select { users.id like "a%" }.count()
            val changed = users.slice(users.name).select { users.id eq "NewName" }.count()
            assertEquals(1, unchanged)
            assertEquals(1, changed)
        }
    }

    @Test
    fun testUpdateWithLimit02() {
        val dialects = TestDB.values().toList() - notSupportLimit - notSupportReturning
        withCitiesAndUsers(dialects) { _, users, _ ->
            expectException<UnsupportedByDialectException> {
                users.updateReturning({ users.id like "a%" }, 1, users) {
                    it[users.id] = "NewName"
                }
            }
        }
    }

    @Test
    fun testUpdateWithReturning01() {
        val dialects = TestDB.values().toList() - notSupportLimit
        withCitiesAndUsers(dialects) { _, users, _ ->
            expectException<UnsupportedByDialectException> {
                users.updateReturning({ users.id like "a%" }, 1, users) {
                    it[users.id] = "NewName"
                }
            }
        }
    }

    @Test
    fun testUpdateWithJoin01() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { _, users, userData ->
            val join = users.innerJoin(userData)
            join.update {
                it[userData.comment] = users.name
                it[userData.value] = 123
            }

            join.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
                assertEquals(123, it[userData.value])
            }
        }
    }
    @Test
    fun testUpdateWithJoin02() {
        withCitiesAndUsers(exclude = TestDB.allH2TestDB + TestDB.SQLITE) { cities, users, userData ->
            val join = cities.innerJoin(users).innerJoin(userData)
            join.update {
                it[userData.comment] = users.name
                it[userData.value] = 123
            }

            join.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
                assertEquals(123, it[userData.value])
            }
        }
    }

    @Test
    fun `test that column length checked in update `() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            stringTable.insert {
                it[name] = "TestName"
            }

            val veryLongString = "1".repeat(255)
            expectException<IllegalArgumentException> {
                stringTable.updateReturning({ stringTable.name eq "TestName" }, null, stringTable) {
                    it[name] = veryLongString
                }
            }
        }
    }

    @Test
    fun `test update fails with empty body`() {
        withCitiesAndUsers { cities, _, _ ->
            expectException<IllegalArgumentException> {
                cities.updateReturning(where = { cities.id.isNull() }, null, cities) {
                    // empty
                }
            }
        }
    }

    @Test
    fun `update encryptedColumnType`() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = encryptedVarchar("name", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
            val city = encryptedBinary("city", 100, Algorithms.AES_256_PBE_CBC("passwd", "12345678"))
            val address = encryptedVarchar("address", 100, Algorithms.BLOW_FISH("key"))
        }

        withTables(stringTable) {
            val id = stringTable.insertAndGetId {
                it[name] = "TestName"
                it[city] = "TestCity".toByteArray()
                it[address] = "TestAddress"
            }

            val updatedName = "TestName2"
            val updatedCity = "TestCity2"
            val updatedAddress = "TestAddress2"
            stringTable.updateReturning({ stringTable.id eq id }, null, stringTable) {
                it[name] = updatedName
                it[city] = updatedCity.toByteArray()
                it[address] = updatedAddress
            }

            assertEquals(updatedName, stringTable.select { stringTable.id eq id }.single()[stringTable.name])
            assertEquals(updatedCity, String(stringTable.select { stringTable.id eq id }.single()[stringTable.city]))
            assertEquals(updatedAddress, stringTable.select { stringTable.id eq id }.single()[stringTable.address])
        }
    }
}
