package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

private inline fun <reified T : Any> Table.array3(name: String, maximumCardinality: List<Int>? = null): Column<List<List<List<T>>>> =
    array<T, List<List<List<T>>>>(name, maximumCardinality, dimensions = 3)

private inline fun <reified T : Any> Table.array2(name: String, maximumCardinality: List<Int>? = null): Column<List<List<T>>> =
    array<T, List<List<T>>>(name, maximumCardinality, dimensions = 2)

class MultiArrayColumnTypeTests : DatabaseTestsBase() {

    private val multiArrayTypeUnsupportedDb = TestDB.ALL - TestDB.ALL_POSTGRES.toSet()

    @Test
    fun test2xMultiArray() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
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
            val multiArray = array3<Int>("multi_array")
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
            val multiArray = array<String, List<List<List<List<List<String>>>>>>("multi_array", dimensions = 5)
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
            val multiArray = array2<Int>("multi_array")
                .default(default)
        }

        val testerDatabaseGenerated = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
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
            val multiArray = array2<Int>("multi_array", maximumCardinality = listOf(2, 2))
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            tester.insert {
                it[tester.multiArray] = list
            }

            assertEqualLists(list.flatten(), tester.selectAll().first()[tester.multiArray].flatten())
        }
    }

    @Test
    fun testMultiArrayWithNullable() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
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
            val multiArray = array2<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(listOf(1, 2), listOf(3, 4))

            tester.insert {
                it[multiArray] = arrayLiteral<Int, List<List<Int>>>(list, dimensions = 2)
            }

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(list.flatten(), value.flatten())
        }
    }

    @Test
    fun testMultiArrayParam() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            val list = listOf(listOf(1, 2), listOf(3, 4))

            tester.insert {
                it[multiArray] = arrayParam<Int, List<List<Int>>>(list, dimensions = 2)
            }

            val value = tester.selectAll().first()[tester.multiArray]
            assertEqualLists(list.flatten(), value.flatten())
        }
    }

    @Test
    fun testMultiArrayUpdate() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
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
            val multiArray = array2<Int>("multi_array")
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

    @Test
    fun testMultiArrayGetFunction() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            tester.batchInsert(
                listOf(
                    listOf(listOf(1, 1), listOf(1, 4)),
                    listOf(listOf(1, 1), listOf(2, 4)),
                    listOf(listOf(1, 1), listOf(1, 6)),
                )
            ) {
                this[tester.multiArray] = it
            }

            val values = tester.selectAll().where { tester.multiArray[2][2] eq 4 }.map { it[tester.multiArray] }
            assertEquals(2, values.size)
            assertEqualLists(
                listOf(
                    listOf(listOf(1, 1), listOf(1, 4)),
                    listOf(listOf(1, 1), listOf(2, 4)),
                ),
                values
            )

            assertEquals(0, tester.selectAll().where { tester.multiArray[2][2] greater 10 }.map { it[tester.multiArray] }.size)
        }
    }

    @Test
    fun testMultiArraySliceFunction() {
        val tester = object : IntIdTable("test_table") {
            val multiArray = array2<Int>("multi_array")
        }

        withTables(excludeSettings = multiArrayTypeUnsupportedDb, tester) {
            tester.insert {
                it[multiArray] = listOf(
                    listOf(1, 2, 3, 4), listOf(5, 6, 7, 8), listOf(9, 10, 11, 12), listOf(13, 14, 15, 16)
                )
            }

            val alias = tester.multiArray.slice(1, 2).slice(2, 3)

            val query = tester.select(alias).first()
            assertEqualLists(listOf(2, 3, 6, 7), query[alias].flatten())
        }
    }

    object MultiArrayTable : IntIdTable() {
        val multiArray = array2<Int>("multi_array")
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
