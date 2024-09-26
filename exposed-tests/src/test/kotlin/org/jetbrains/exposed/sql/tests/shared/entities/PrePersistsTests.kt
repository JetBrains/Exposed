package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class PrePersistsTests : DatabaseTestsBase() {
    object TestTable : IntIdTable("test_table") {
        val value = text("value")
        val nullableValue = text("nullableValue").nullable()
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value
            .prePersist { it.uppercase() }
        var nullableValue by TestTable.nullableValue
            .prePersist { it?.uppercase() }

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    @Test
    fun testPrePersis() {
        withTables(TestTable) {
            val entity = TestEntity.new {
                value = "test-value"
                nullableValue = "nullable-test-value"
            }

            assertEquals("TEST-VALUE", entity.value)
            assertEquals("NULLABLE-TEST-VALUE", entity.nullableValue)

            TestTable.selectAll().first().let { entry ->
                assertEquals("TEST-VALUE", entry[TestTable.value])
                assertEquals("NULLABLE-TEST-VALUE", entry[TestTable.nullableValue])
            }
        }
    }
}
