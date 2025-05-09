package org.jetbrains.exposed.v1.sql.tests.shared.dml

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.assertTrue
import org.junit.Test
import java.util.*
import kotlin.test.assertContentEquals

class ReplaceTests : DatabaseTestsBase() {

    private val replaceNotSupported = TestDB.ALL - TestDB.ALL_MYSQL_LIKE - TestDB.SQLITE + TestDB.ALL_H2_V1

    private object NewAuth : Table("new_auth") {
        val username = varchar("username", 16)
        val session = binary("session", 64)
        val timestamp = long("timestamp").default(0)
        val serverID = varchar("serverID", 64).default("")

        override val primaryKey = PrimaryKey(username)
    }

    @Test
    fun testReplaceSelect() {
        withTables(replaceNotSupported, NewAuth) { testDb ->
            NewAuth.batchReplace(listOf("username1", "username2")) { // inserts 2 new non-conflict rows with defaults
                this[NewAuth.username] = it
                this[NewAuth.session] = "session".toByteArray()
            }

            val result1 = NewAuth.selectAll().toList()
            assertTrue(result1.all { it[NewAuth.timestamp] == 0L })
            assertTrue(result1.all { it[NewAuth.serverID].isEmpty() })

            val timeNow = System.currentTimeMillis()
            val specialId = "special server id"
            val allRowsWithNewDefaults = NewAuth.select(NewAuth.username, NewAuth.session, longLiteral(timeNow), stringLiteral(specialId))

            val affectedRowCount = NewAuth.replace(allRowsWithNewDefaults)
            // MySQL returns 1 for every insert + 1 for every delete on conflict, while others only count inserts
            val expectedRowCount = if (testDb in TestDB.ALL_MYSQL_LIKE) 4 else 2
            assertEquals(expectedRowCount, affectedRowCount)

            val result2 = NewAuth.selectAll().toList()
            assertTrue(result2.all { it[NewAuth.timestamp] == timeNow })
            assertTrue(result2.all { it[NewAuth.serverID] == specialId })
        }
    }

    @Test
    fun testReplaceSelectWithSpecificColumns() {
        withTables(replaceNotSupported, NewAuth) { testDb ->
            val (name1, name2, oldSession) = Triple("username1", "username2", "session1".toByteArray())
            NewAuth.batchReplace(listOf(name1, name2)) { // inserts 2 new non-conflict rows with defaults
                this[NewAuth.username] = it
                this[NewAuth.session] = oldSession
            }

            val newSession = "session2"
            val name1Row = NewAuth.select(NewAuth.username, stringLiteral(newSession)).where { NewAuth.username eq name1 }

            val affectedRowCount = NewAuth.replace(name1Row, columns = listOf(NewAuth.username, NewAuth.session))
            // MySQL returns 1 for every insert + 1 for every delete on conflict, while others only count inserts
            val expectedRowCount = if (testDb in TestDB.ALL_MYSQL_LIKE) 2 else 1
            assertEquals(expectedRowCount, affectedRowCount)

            val name1Result = NewAuth.selectAll().where { NewAuth.username eq name1 }.single()
            assertContentEquals(newSession.toByteArray(), name1Result[NewAuth.session])

            val name2Result = NewAuth.selectAll().where { NewAuth.username eq name2 }.single()
            assertContentEquals(oldSession, name2Result[NewAuth.session])
        }
    }

    @Test
    fun testReplaceWithPKConflict() {
        withTables(replaceNotSupported, NewAuth) {
            val (name1, session1) = "username" to "session"
            NewAuth.replace { // replace, like any insert, should accept defaults
                it[username] = name1
                it[session] = session1.toByteArray()
            }

            val result1 = NewAuth.selectAll().single()
            assertEquals(0, result1[NewAuth.timestamp])
            assertTrue(result1[NewAuth.serverID].isEmpty())

            val timeNow = System.currentTimeMillis()
            val concatId = "$name1-$session1"
            NewAuth.replace {
                it[username] = name1
                it[session] = session1.toByteArray()
                it[timestamp] = timeNow
                it[serverID] = concatId
            }

            val result2 = NewAuth.selectAll().single()
            assertEquals(timeNow, result2[NewAuth.timestamp])
            assertEquals(concatId, result2[NewAuth.serverID])
        }
    }

    @Test
    fun testReplaceWithCompositePKConflict() {
        val tester = object : Table("test_table") {
            val key1 = varchar("key_1", 16)
            val key2 = varchar("key_2", 16)
            val replaced = long("replaced").default(0)

            override val primaryKey = PrimaryKey(key1, key2)
        }

        withTables(replaceNotSupported, tester) {
            val (id1, id2) = "A" to "B"
            tester.replace {
                it[key1] = id1
                it[key2] = id2
            }

            assertEquals(0, tester.selectAll().single()[tester.replaced])

            val timeNow = System.currentTimeMillis()
            tester.replace { // insert because only 1 constraint is equal
                it[key1] = id1
                it[key2] = "$id2 2"
                it[replaced] = timeNow
            }

            assertEquals(2, tester.selectAll().count())
            assertEquals(0, tester.selectAll().where { tester.key2 eq id2 }.single()[tester.replaced])

            tester.replace { // delete & insert because both constraints match
                it[key1] = id1
                it[key2] = id2
                it[replaced] = timeNow
            }

            assertEquals(2, tester.selectAll().count())
            assertEquals(timeNow, tester.selectAll().where { tester.key2 eq id2 }.single()[tester.replaced])
        }
    }

    @Test
    fun testReplaceWithExpression() {
        withTables(replaceNotSupported, NewAuth) {
            NewAuth.replace {
                it[username] = "username"
                it[session] = "session".toByteArray()
                it[serverID] = stringLiteral("  serverID1 ").trim()
            }

            assertEquals("serverID1", NewAuth.selectAll().single()[NewAuth.serverID])
        }
    }

    @Test
    fun testEmptyReplace() {
        val tester = object : Table("tester") {
            val id = integer("id").autoIncrement()

            override val primaryKey = PrimaryKey(id)
        }

        withTables(replaceNotSupported, tester) {
            tester.replace { }

            assertEquals(1, tester.selectAll().count())
        }
    }

    @Test
    fun testBatchReplace01() {
        withCitiesAndUsers(replaceNotSupported) { cities, users, userData ->
            val (munichId, pragueId, saintPetersburgId) = cities.select(cities.id)
                .where { cities.name inList listOf("Munich", "Prague", "St. Petersburg") }
                .orderBy(cities.name).map { it[cities.id] }

            // replace is implemented as delete-then-insert on conflict, which breaks foreign key constraints,
            // so this test will only work if those related rows are deleted.
            userData.deleteAll()
            users.deleteAll()

            val cityUpdates = listOf(
                munichId to "München",
                pragueId to "Prague",
                saintPetersburgId to "Saint Petersburg"
            )

            cities.batchReplace(cityUpdates) {
                this[cities.id] = it.first
                this[cities.name] = it.second
            }

            val cityNames = cities.select(cities.name)
                .where { cities.id inList cityUpdates.unzip().first }
                .orderBy(cities.name).toCityNameList()

            assertEqualLists(cityUpdates.unzip().second, cityNames)
        }
    }

    @Test
    fun testBatchReplaceWithSequence() {
        val cities = DMLTestsData.Cities
        withTables(replaceNotSupported, cities) {
            val amountOfNames = 25
            val names = List(amountOfNames) { index -> index + 1 to UUID.randomUUID().toString() }.asSequence()

            cities.batchReplace(names) { (index, name) ->
                this[cities.id] = index
                this[cities.name] = name
            }

            val namesFromDB1 = cities.selectAll().toCityNameList()
            assertEquals(amountOfNames, namesFromDB1.size)
            assertEqualLists(names.unzip().second, namesFromDB1)

            val namesToReplace = List(amountOfNames) { index -> index + 1 to UUID.randomUUID().toString() }.asSequence()

            cities.batchReplace(namesToReplace) { (index, name) ->
                this[cities.id] = index
                this[cities.name] = name
            }

            val namesFromDB2 = cities.selectAll().toCityNameList()
            assertEquals(amountOfNames, namesFromDB2.size)
            assertEqualLists(namesToReplace.unzip().second, namesFromDB2)
        }
    }

    @Test
    fun testBatchReplaceWithEmptySequence() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = emptySequence<String>()
            cities.batchReplace(names) { name -> this[cities.name] = name }

            val batchesSize = cities.selectAll().count()

            assertEquals(0, batchesSize)
        }
    }
}
