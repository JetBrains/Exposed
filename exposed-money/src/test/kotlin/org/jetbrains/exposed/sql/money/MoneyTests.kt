package org.jetbrains.exposed.sql.money

import org.javamoney.moneta.Money
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

open class MoneyBaseTest : DatabaseTestsBase() {

    @Test
    fun testConvertFromDB() {
        val type = MoneyColumnType(10)
        val oneBuck = Money.of(BigDecimal.ONE, "USD")
        assertEquals(oneBuck, type.valueFromDB("USD1"))
        assertEquals(oneBuck, type.valueFromDB(oneBuck))
    }

    @Test
    fun testInsertSelectMoney() {
        testInsertedAndSelect(Money.of(BigDecimal.TEN, "USD"))
    }

    @Test
    fun testInsertSelectFloatingMoney() {
        testInsertedAndSelect(Money.of(BigDecimal("0.12345678"), "USD"))
    }

    @Test
    fun testInsertSelectNull() {
        testInsertedAndSelect(null)
    }

    @Test(expected = ExposedSQLException::class)
    fun testInsertSelectOutOfLength() {
        testInsertedAndSelect(Money.of(BigDecimal.valueOf(12345678901), "CZK"))
    }

    private fun testInsertedAndSelect(toInsert: Money?) {
        withTables(Account) {
            val accountID = Account.insertAndGetId {
                it[name] = "My USD account"
                it[my_money] = toInsert
            }

            val inserted = Account.slice(Account.my_money).select { Account.id.eq(accountID) }.single()[Account.my_money]

            assertEquals(toInsert, inserted)
        }
    }

}

object Account : IntIdTable("Account") {
    val name = varchar("name", 50) // Column<String>
    val my_money = money("my_money", 10).nullable() // Column<datetime>
}