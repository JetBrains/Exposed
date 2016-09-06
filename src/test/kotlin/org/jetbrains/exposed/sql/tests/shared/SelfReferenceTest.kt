package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("unused")
class SortByReferenceTest {

    @Test
    fun simpleTest() {
        assertEqualLists(listOf(DMLTestsData.Cities), EntityCache.sortTablesByReferences(listOf(DMLTestsData.Cities)))
        assertEqualLists(listOf(DMLTestsData.Users), EntityCache.sortTablesByReferences(listOf(DMLTestsData.Users)))

        val rightOrder = listOf(DMLTestsData.Cities, DMLTestsData.Users, DMLTestsData.UserData)
        val r1 = EntityCache.sortTablesByReferences(listOf(DMLTestsData.Cities, DMLTestsData.UserData, DMLTestsData.Users))
        val r2 = EntityCache.sortTablesByReferences(listOf(DMLTestsData.UserData, DMLTestsData.Cities, DMLTestsData.Users))
        val r3 = EntityCache.sortTablesByReferences(listOf(DMLTestsData.Users, DMLTestsData.Cities, DMLTestsData.UserData))
        assertEqualLists(r1, rightOrder)
        assertEqualLists(r2, rightOrder)
        assertEqualLists(r3, rightOrder)
    }

    @Test
    fun cycleReferencesCheckTest() {
        val cities = object : Table() {
            val id = integer("id").autoIncrement().primaryKey()
            val name = varchar("name", 50)
            val strange_id = varchar("strange_id", 10)
        }

        val users = object : Table() {
            val id = varchar("id", 10).primaryKey()
            val name = varchar("name", length = 50)
            val cityId = (integer("city_id") references cities.id).nullable()
        }

        val noRefereeTable = object: Table() {
            val id = varchar("id", 10).primaryKey()
            val col1 = varchar("col1", 10)
        }

        val refereeTable = object: Table() {
            val id = varchar("id", 10).primaryKey()
            val ref = reference("ref", noRefereeTable.id)
        }

        val referencedTable = object: IntIdTable() {
            val col3 = varchar("col3", 10)
        }

        val strangeTable = object : Table() {
            val id = varchar("id", 10).primaryKey()
            val user_id = varchar("user_id", 10) references users.id
            val comment = varchar("comment", 30)
            val value = integer("value")
        }

        with (cities) {
            strange_id.references( strangeTable.id)
        }

        val sortedTables = EntityCache.sortTablesByReferences(listOf(cities, users, strangeTable, noRefereeTable, refereeTable, referencedTable))

        assert(sortedTables.indexOf(referencedTable) in listOf(0,1))
        assert(sortedTables.indexOf(noRefereeTable) in listOf(0,1))
        assertEquals(2, sortedTables.indexOf(refereeTable))

        assert(sortedTables.indexOf(cities) in listOf(3,4,5))
        assert(sortedTables.indexOf(users) in listOf(3,4,5))
        assert(sortedTables.indexOf(strangeTable) in listOf(3,4,5))
    }
}