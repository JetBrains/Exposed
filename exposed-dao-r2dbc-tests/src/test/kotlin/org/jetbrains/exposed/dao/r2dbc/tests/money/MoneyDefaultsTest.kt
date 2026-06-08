package org.jetbrains.exposed.dao.r2dbc.tests.money

import kotlinx.coroutines.flow.toList
import org.javamoney.moneta.Money
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.money.compositeMoney
import org.jetbrains.exposed.v1.money.nullable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.assertNull
import java.math.BigDecimal
import kotlin.test.Test

class MoneyDefaultsTest : R2dbcDatabaseTestsBase() {
    object TableWithDBDefault : IntIdTable() {
        val defaultValue: Money = Money.of(BigDecimal.ONE, "USD")

        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = compositeMoney(10, 0, "t1").default(defaultValue)
        val t2 = compositeMoney(10, 0, "t2").nullable()
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>) : IntEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        var t2 by TableWithDBDefault.t2
        val clientDefault by TableWithDBDefault.clientDefault

        override fun hashCode(): Int = id.value.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DBDefault) return false
            if (other.t1 != other.t1) return false
            if (other.t2 != other.t2) return false
            if (other.clientDefault != other.clientDefault) return false

            return true
        }

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testDefaultsWithExplicit() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                DBDefault.new { field = "1" }.flush(),
                DBDefault.new {
                    field = "2"
                    t1 = Money.of(BigDecimal.TEN, "USD")
                }.flush()
            )
            created.forEach {
                DBDefault.removeFromCache(it)
            }

            val entities = DBDefault.all().toList()
            assertEqualCollections(created.map { it.id }, entities.map { it.id })
        }
    }

    @Test
    fun testDefaultsInvokedOnlyOncePerEntity() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }.flush()
            val db2 = DBDefault.new { field = "2" }.flush()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
            assertEquals(TableWithDBDefault.defaultValue, db1.t1)
        }
    }

    @Test
    fun testNullableCompositeColumnType() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new { field = "1" }.flush()
            assertNull(db1.t2)
            val money = Money.of(BigDecimal.ONE, "USD")
            db1.t2 = money
            db1.refresh(flush = true)
            assertEquals(money, db1.t1)
            assertEquals(TableWithDBDefault.defaultValue, db1.t1)
        }
    }
}
