package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.Table
import org.junit.Test

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

    @Test(expected = IllegalStateException::class)
    fun cycleReferencesCheckTest() {
        @Suppress("unused")
        val cities = object : Table() {
            val id = integer("id").autoIncrement().primaryKey()
            val name = varchar("name", 50)
            val strange_id = varchar("strange_id", 10)
        }

        @Suppress("unused")
        val users = object : Table() {
            val id = varchar("id", 10).primaryKey()
            val name = varchar("name", length = 50)
            val cityId = (integer("city_id") references cities.id).nullable()
        }

        @Suppress("unused")
        val strangeTable = object : Table() {
            val id = varchar("id", 10).primaryKey()
            val user_id = varchar("user_id", 10) references users.id
            val comment = varchar("comment", 30)
            val value = integer("value")
        }

        with (cities) {
            strange_id.references( strangeTable.id)
        }
        EntityCache.sortTablesByReferences(listOf(cities, users, strangeTable))
    }

}