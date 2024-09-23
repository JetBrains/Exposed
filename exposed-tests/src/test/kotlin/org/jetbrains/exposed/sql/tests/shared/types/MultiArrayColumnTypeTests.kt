package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.multi2ArrayLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.junit.Test
import kotlin.test.assertNull

class MultiArrayColumnTypeTests : DatabaseTestsBase() {

    private val multiArrayTypeUnsupportedDb = TestDB.ALL - TestDB.ALL_POSTGRES.toSet()

    @Test
    fun test2xMultiArray() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
            val statement = tester.insert {
                it[multiArray] = list
            }
            assertEqualLists(list.flatten(), statement[tester.multiArray].flatten())

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(list.flatten(), value.flatten())
        }
    }

    @Test
    fun test3xMultiArray() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi3Array<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(
                listOf(listOf(1, 2), listOf(3, 4)),
                listOf(listOf(5, 6), listOf(7, 8))
            )
            tester.insert {
                it[multiArray] = list
            }

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(list.flatten().flatten(), value.flatten().flatten())
        }
    }

    @Test
    fun test5xMultiArray() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multiArray<String, List<List<List<List<List<String>>>>>>("multi_array", 5)
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(listOf(listOf(listOf(listOf("Hallo", "MultiDimensional", "Array")))))
            tester.insert {
                it[multiArray] = list
            }

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(
                list.flatten().flatten().flatten().flatten(),
                value.flatten().flatten().flatten().flatten()
            )
        }
    }

    @Test
    fun testMultiArrayDefault() {
        val default = listOf(listOf(1, 2), listOf(3, 4))

        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
                .default(default)
        }

        val testerDatabaseGenerated = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
                .databaseGenerated()
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val statement = testerDatabaseGenerated.insert {}
            assertEqualLists(default.flatten(), statement[testerDatabaseGenerated.multiArray].flatten())

            val value = testerDatabaseGenerated.selectAll().first()[tester.multiArray]
            assertEqualLists(default.flatten(), value.flatten())
        }
    }

    @Test
    fun testMultiArrayCardinality() {
        val list = listOf(listOf(1, 2, 3), listOf(4, 5, 6))

        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array", maximumCardinality = listOf(2, 2))
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            expectException<IllegalArgumentException> {
                tester.insert {
                    it[tester.multiArray] = list
                }
            }
        }
    }

    @Test
    fun testMultiArrayWithNullable() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
                .nullable()
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val statement = tester.insert {
                it[multiArray] = null
            }
            assertNull(statement[tester.multiArray])
            assertNull(tester.selectAll().first()[tester.multiArray])
        }
    }

    @Test
    fun testMultiArrayLiteral() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(listOf(1, 2), listOf(3, 4))

            tester.insert {
                it[multiArray] = multi2ArrayLiteral(list)
            }

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(list.flatten(), value.flatten())
        }
    }

    @Test
    fun testMultiArrayUpdate() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val initialArray = listOf(listOf(1, 2), listOf(3, 4))

            val insertedId = tester.insert {
                it[multiArray] = initialArray
            } get tester.id

            var value = tester.selectAll().where { tester.id eq insertedId }.first()[tester.multiArray]
            assertEqualLists(initialArray.flatten(), value.flatten())

            val updatedArray = listOf(listOf(5, 6), listOf(7, 8))

            // Perform the update
            tester.update({ tester.id eq insertedId }) {
                it[multiArray] = updatedArray
            }

            value = tester.selectAll().where { tester.id eq insertedId }.first()[tester.multiArray]
            assertEqualLists(updatedArray.flatten(), value.flatten())
        }
    }

    @Test
    fun testMultiArrayUpsert() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = multi2Array<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val initialArray = listOf(listOf(1, 2), listOf(3, 4))

            val id = tester.insertAndGetId {
                it[multiArray] = initialArray
            }

            var value = tester.selectAll().where { tester.id eq id }.first()[tester.multiArray]
            assertEqualLists(initialArray.flatten(), value.flatten())

            val updatedArray = listOf(listOf(5, 6), listOf(7, 8))

            tester.upsert(tester.id, onUpdate = { it[tester.multiArray] = updatedArray }) {
                it[tester.id] = id
                it[multiArray] = initialArray
            }

            value = tester.selectAll().where { tester.id eq id }.first()[tester.multiArray]
            assertEqualLists(updatedArray.flatten(), value.flatten())

            tester.upsert(tester.id) {
                it[multiArray] = updatedArray
            }

            assertEquals(2, tester.selectAll().count())
        }
    }

    object MultiArrayTable : IntIdTable() {
        val multiArray = multi2Array<Int>("multi_array")
    }

    class MultiArrayEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<MultiArrayEntity>(MultiArrayTable)

        var multiArray by MultiArrayTable.multiArray
    }

    @Test
    fun testMultiArrayEntityCreate() {
        withTables(excludeSettings = multiArrayTypeUnsupportedDb, MultiArrayTable) {
            val initialArray = listOf(listOf(1, 2), listOf(3, 4))

            val entity = MultiArrayEntity.new {
                multiArray = initialArray
            }

            assertEqualLists(initialArray.flatten(), entity.multiArray.flatten())

            val fetchedList = MultiArrayEntity.findById(entity.id)?.multiArray
            assertEqualLists(initialArray.flatten(), fetchedList!!.flatten())
        }
    }

    @Test
    fun testMultiArrayEntityUpdate() {
        withTables(excludeSettings = multiArrayTypeUnsupportedDb, MultiArrayTable) {
            val initialArray = listOf(listOf(1, 2), listOf(3, 4))

            val entity = MultiArrayEntity.new {
                multiArray = initialArray
            }

            val updatedArray = listOf(listOf(5, 6), listOf(7, 8))
            entity.multiArray = updatedArray

            val fetchedEntity = MultiArrayEntity.findById(entity.id)
            assertEquals(entity, fetchedEntity)
            assertEqualLists(updatedArray.flatten(), fetchedEntity!!.multiArray.flatten())
        }
    }
}
