package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.statements.ReturningStatement
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReturningTests : DatabaseTestsBase() {
    private val returningSupportedDb = TestDB.ALL_POSTGRES.toSet() + TestDB.SQLITE + TestDB.MARIADB

    object Items : IntIdTable("items") {
        val name = varchar("name", 32)
        val price = double("price")
    }

    class ItemDAO(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ItemDAO>(Items)

        var name by Items.name
        var price by Items.price
    }

    @Test
    fun testInsertReturning() {
        withTables(TestDB.ALL - returningSupportedDb, Items) {
            // return all columns by default
            val result1 = Items.insertReturning {
                it[name] = "A"
                it[price] = 99.0
            }.single()
            assertEquals(1, result1[Items.id].value)
            assertEquals("A", result1[Items.name])
            assertEquals(99.0, result1[Items.price])

            val result2 = Items.insertReturning(listOf(Items.id, Items.name)) {
                it[name] = "B"
                it[price] = 200.0
            }.single()
            assertEquals(2, result2[Items.id].value)
            assertEquals("B", result2[Items.name])

            assertFailsWith<IllegalStateException> { // Items.price not in record set
                result2[Items.price]
            }

            assertEquals(2, Items.selectAll().count())
        }
    }

    @Test
    fun testInsertIgnoreReturning() {
        val tester = object : Table("tester") {
            val item = varchar("item", 32).uniqueIndex()
        }

        withTables(TestDB.ALL - returningSupportedDb, tester) {
            tester.insert {
                it[item] = "Item A"
            }
            assertEquals(1, tester.selectAll().count())

            // no result set is returned because insert is ignored
            val resultWithConflict = tester.insertReturning(ignoreErrors = true) {
                it[item] = "Item A"
            }.toList()

            assertTrue { resultWithConflict.isEmpty() }
            assertEquals(1, tester.selectAll().count())

            val resultWithoutConflict = tester.insertReturning(ignoreErrors = true) {
                it[item] = "Item B"
            }.single()

            assertEquals("Item B", resultWithoutConflict[tester.item])
            assertEquals(2, tester.selectAll().count())
        }
    }

    @Test
    fun testUpsertReturning() {
        withTables(TestDB.ALL - returningSupportedDb + TestDB.MARIADB, Items) {
            // return all columns by default
            val result1 = Items.upsertReturning {
                it[name] = "A"
                it[price] = 99.0
            }.single()
            assertEquals(1, result1[Items.id].value)
            assertEquals("A", result1[Items.name])
            assertEquals(99.0, result1[Items.price])

            val result2 = Items.upsertReturning(
                returning = listOf(Items.name, Items.price),
                onUpdate = { it[Items.price] = Items.price times 10.0 }
            ) {
                it[id] = 1
                it[name] = "B"
                it[price] = 200.0
            }.single()
            assertEquals("A", result2[Items.name])
            assertEquals(990.0, result2[Items.price])

            val result3 = Items.upsertReturning(
                returning = listOf(Items.name),
                onUpdateExclude = listOf(Items.price),
                where = { Items.price greater 500.0 }
            ) {
                it[id] = 1
                it[name] = "B"
                it[price] = 200.0
            }.single()
            assertEquals("B", result3[Items.name])

            assertEquals(1, Items.selectAll().count())
        }
    }

    @Test
    fun testUpsertReturningWithDAO() {
        withTables(TestDB.ALL - returningSupportedDb, Items) {
            val result1 = Items.upsertReturning {
                it[name] = "A"
                it[price] = 99.0
            }.let {
                ItemDAO.wrapRow(it.single())
            }
            assertEquals(1, result1.id.value)
            assertEquals("A", result1.name)
            assertEquals(99.0, result1.price)

            val result2 = Items.upsertReturning {
                it[id] = 1
                it[name] = "B"
                it[price] = 200.0
            }.let {
                ItemDAO.wrapRow(it.single())
            }
            assertEquals(1, result2.id.value)
            assertEquals("B", result2.name)
            assertEquals(200.0, result2.price)

            assertEquals(1, Items.selectAll().count())
        }
    }

    @Test
    fun testReturningWithNoResults() {
        withTables(TestDB.enabledDialects() - returningSupportedDb, Items) {
            // statement not executed if not iterated over
            val stmt = Items.insertReturning {
                it[name] = "A"
                it[price] = 99.0
            }
            assertIs<ReturningStatement>(stmt)

            assertEquals(0, Items.selectAll().count())

            assertTrue { Items.deleteReturning().toList().isEmpty() }
        }
    }

    @Test
    fun testDeleteReturning() {
        withTables(TestDB.enabledDialects() - returningSupportedDb, Items) {
            Items.batchInsert(listOf("A" to 99.0, "B" to 100.0, "C" to 200.0)) { (n, p) ->
                this[Items.name] = n
                this[Items.price] = p
            }

            assertEquals(3, Items.selectAll().count())

            // return all columns by default
            val result1 = Items.deleteReturning(where = { Items.price eq 200.0 }).single()
            assertEquals(3, result1[Items.id].value)
            assertEquals("C", result1[Items.name])
            assertEquals(200.0, result1[Items.price])

            assertEquals(2, Items.selectAll().count())

            val result2 = Items.deleteReturning(listOf(Items.id)).map { it[Items.id].value }
            assertEqualCollections(listOf(1, 2), result2)

            assertEquals(0, Items.selectAll().count())
        }
    }

    @Test
    fun testUpdateReturning() {
        withTables(TestDB.enabledDialects() - returningSupportedDb + TestDB.MARIADB, Items) {
            val input = listOf("A" to 99.0, "B" to 100.0, "C" to 200.0)
            Items.batchInsert(input) { (n, p) ->
                this[Items.name] = n
                this[Items.price] = p
            }

            // return all columns by default
            val result1 = Items.updateReturning(where = { Items.price lessEq 99.0 }) {
                it[price] = price.times(10.0)
            }.single()
            assertEquals(1, result1[Items.id].value)
            assertEquals("A", result1[Items.name])
            assertEquals(990.0, result1[Items.price])

            val result2 = Items.updateReturning(listOf(Items.name)) {
                it[name] = name.lowerCase()
            }.map { it[Items.name] }
            assertEqualCollections(input.map { it.first.lowercase() }, result2)

            val newPrice = Items.price.alias("new_price")
            val result3 = Items.updateReturning(listOf(newPrice)) {
                it[price] = 0.0
            }.map { it[newPrice] }
            assertEquals(3, result3.size)
            assertTrue { result3.all { it == 0.0 } }
        }
    }
}
