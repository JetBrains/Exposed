package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReturningTests : DatabaseTestsBase() {
    private val returningSupportedDb = TestDB.postgreSQLRelatedDB.toSet() + TestDB.SQLITE

    object Items : IntIdTable("items") {
        val name = varchar("name", 32)
        val price = double("price")
    }

    @Test
    fun testInsertReturning() {
        withTables(TestDB.enabledDialects() - returningSupportedDb, Items) {
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
    fun testUpsertReturning() {
        withTables(TestDB.enabledDialects() - returningSupportedDb, Items) {
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
                onUpdate = listOf(Items.price to Items.price.times(10.0))
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
}
