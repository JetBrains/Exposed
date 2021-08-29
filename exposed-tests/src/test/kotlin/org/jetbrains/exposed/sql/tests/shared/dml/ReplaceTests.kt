package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.util.*

class ReplaceTests : DatabaseTestsBase() {

    private val notSupportsReplace = listOf(TestDB.ORACLE, TestDB.SQLSERVER)

    // GitHub issue #98: Parameter index out of range when using Table.replace
    @Test
    fun testReplace01() {
        val NewAuth = object : Table("new_auth") {
            val username = varchar("username", 16)
            val session = binary("session", 64)
            val timestamp = long("timestamp").default(0)
            val serverID = varchar("serverID", 64).default("")

            override val primaryKey = PrimaryKey(username)
        }
        withTables(notSupportsReplace, NewAuth) {
            NewAuth.replace {
                it[username] = "username"
                it[session] = "session".toByteArray()
            }
        }
    }

    @Test
    fun testBatchReplace01() {
        withCitiesAndUsers(notSupportsReplace) { cities, users, userData ->
            val (munichId, pragueId, saintPetersburgId) = cities.slice(cities.id).select {
                cities.name inList listOf("Munich", "Prague", "St. Petersburg")
            }.orderBy(cities.name).map { it[cities.id] }

            // MySQL replace is implemented as deleted-then-insert, which breaks foreign key constraints,
            // so this test will only work if those related rows are deleted.
            if (currentDialect is MysqlDialect) {
                userData.deleteAll()
                users.deleteAll()
            }

            val cityUpdates = listOf(
                munichId to "München",
                pragueId to "Prague",
                saintPetersburgId to "Saint Petersburg"
            )

            cities.batchReplace(cityUpdates) {
                this[cities.id] = it.first
                this[cities.name] = it.second
            }

            val cityNames = cities.slice(cities.name)
                .select { cities.id inList listOf(munichId, pragueId, saintPetersburgId) }
                .orderBy(cities.name).map { it[cities.name] }

            assertEqualLists(listOf("München", "Prague", "Saint Petersburg"), cityNames)
        }
    }

    @Test
    fun `batchReplace using a sequence should work`() {
        val Cities = DMLTestsData.Cities
        withTables(notSupportsReplace, Cities) {
            val names = List(25) { index -> index + 1 to UUID.randomUUID().toString() }.asSequence()

            Cities.batchReplace(names) { (index, name) ->
                this[Cities.id] = index
                this[Cities.name] = name
            }

            val namesFromDB1 = Cities.selectAll().map { it[Cities.name] }
            assertEquals(25, namesFromDB1.size)
            assertEqualLists(names.map { it.second }.toList(), namesFromDB1)

            val namesToReplace = List(25) { index -> index + 1 to UUID.randomUUID().toString() }.asSequence()

            Cities.batchReplace(namesToReplace) { (index, name) ->
                this[Cities.id] = index
                this[Cities.name] = name
            }

            val namesFromDB2 = Cities.selectAll().map { it[Cities.name] }

            assertEquals(25, namesFromDB2.size)
            assertEqualLists(namesToReplace.map { it.second }.toList(), namesFromDB2)
        }
    }

    @Test
    fun `batchInserting using empty sequence should work`() {
        val Cities = DMLTestsData.Cities
        withTables(Cities) {
            val names = emptySequence<String>()
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            val batchesSize = Cities.selectAll().count()

            assertEquals(0, batchesSize)
        }
    }
}
