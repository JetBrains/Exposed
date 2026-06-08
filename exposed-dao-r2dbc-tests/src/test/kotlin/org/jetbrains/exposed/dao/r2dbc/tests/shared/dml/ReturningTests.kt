package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReturningTests : R2dbcDatabaseTestsBase() {
    private val updateReturningSupportedDb = TestDB.ALL_POSTGRES.toSet()
    private val returningSupportedDb = updateReturningSupportedDb + TestDB.MARIADB

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
}
