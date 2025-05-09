package org.jetbrains.exposed.v1.sql.money

import org.javamoney.moneta.Money
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.expectException
import org.junit.Test
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount

private const val AMOUNT_SCALE = 5

open class MoneyBaseTest : DatabaseTestsBase() {

    @Test
    fun testInsertSelectMoney() {
        withTables(Account) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(Money.of(BigDecimal.TEN, "USD"))
            Account.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(Money.of(BigDecimal.TEN, "USD"))
        }
    }

    @Test
    fun testInsertSelectFloatingMoney() {
        withTables(Account) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(Money.of(BigDecimal("0.12345"), "USD"))
            Account.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(Money.of(BigDecimal("0.12345"), "USD"))
        }
    }

    @Test
    fun testInsertSelectNull() {
        withTables(Account) {
            assertInsertOfCompositeValueReturnsEquivalentOnSelect(null)
            Account.deleteAll()
            assertInsertOfComponentValuesReturnsEquivalentOnSelect(null)
        }
    }

    @Test
    fun testInsertSelectOutOfLength() {
        val amount = BigDecimal.valueOf(12345678901)
        val toInsert = Money.of(amount, "CZK")
        withTables(excludeSettings = listOf(TestDB.SQLITE), Account) {
            expectException<ExposedSQLException> {
                Account.insertAndGetId {
                    it[composite_money] = toInsert
                }
            }

            expectException<ExposedSQLException> {
                Account.insertAndGetId {
                    it[composite_money.amount] = amount
                    it[composite_money.currency] = toInsert.currency
                }
            }
        }
    }

    @Test
    fun testSearchByCompositeColumn() {
        val money = Money.of(BigDecimal.TEN, "USD")

        withTables(Account) {
            Account.insertAndGetId {
                it[composite_money] = money
            }

            val predicates = listOf(
                Account.composite_money eq money,
                (Account.composite_money.currency eq money.currency),
                (Account.composite_money.amount eq BigDecimal.TEN)
            )

            predicates.forEach {
                val found = AccountDao.find { it }

                assertEquals(1L, found.count())
                val next = found.iterator().next()
                assertEquals(money, next.money)
                assertEquals(money.currency, next.currency)
                assertEquals(BigDecimal.TEN.setScale(AMOUNT_SCALE), next.amount)
            }
        }
    }

    @Test
    fun testUsingManualCompositeMoneyColumns() {
        val tester = object : Table("tester") {
            val money = compositeMoney(
                decimal("amount", 8, AMOUNT_SCALE),
                currency("currency")
            )
            val nullableMoney = compositeMoney(
                decimal("nullable_amount", 8, AMOUNT_SCALE).nullable(),
                currency("nullable_currency").nullable()
            )
        }

        withTables(tester) {
            val amount = BigDecimal(99).setScale(AMOUNT_SCALE)
            val currencyUnit = Monetary.getCurrency("EUR")
            tester.insert {
                it[money.amount] = amount
                it[money.currency] = currencyUnit
                it[nullableMoney.amount] = null
                it[nullableMoney.currency] = null
            }

            val result1 = tester
                .selectAll()
                .where { tester.nullableMoney.amount.isNull() and tester.nullableMoney.currency.isNull() }
                .single()
            assertEquals(amount, result1[tester.money.amount])

            tester.update {
                it[tester.nullableMoney.amount] = amount
                it[tester.nullableMoney.currency] = currencyUnit
            }

            val result2 = tester
                .select(tester.money.currency, tester.nullableMoney.currency)
                .where { tester.money.amount.isNotNull() and tester.nullableMoney.amount.isNotNull() }
                .single()
            assertEquals(currencyUnit, result2[tester.money.currency])
            assertEquals(currencyUnit, result2[tester.nullableMoney.currency])

            // manual composite columns should still accept composite values
            val compositeMoney = Money.of(BigDecimal(10), "CAD")
            tester.insert {
                it[money] = compositeMoney
                it[nullableMoney] = null
            }
            tester.insert {
                it[money] = compositeMoney
            }

            assertEquals(2, tester.selectAll().where { tester.nullableMoney eq null }.count())
        }
    }

    private fun JdbcTransaction.assertInsertOfCompositeValueReturnsEquivalentOnSelect(toInsert: Money?) {
        val accountID = Account.insertAndGetId {
            it[composite_money] = toInsert
        }

        val single = Account.select(Account.composite_money).where { Account.id.eq(accountID) }.single()
        val inserted = single[Account.composite_money]

        assertEquals(toInsert, inserted)
    }

    private fun JdbcTransaction.assertInsertOfComponentValuesReturnsEquivalentOnSelect(toInsert: Money?) {
        val amount: BigDecimal? = toInsert?.numberStripped?.setScale(AMOUNT_SCALE)
        val currencyUnit: CurrencyUnit? = toInsert?.currency
        val accountID = Account.insertAndGetId {
            it[composite_money.amount] = amount
            it[composite_money.currency] = currencyUnit
        }

        val single = Account.select(Account.composite_money).where { Account.id eq accountID }.single()

        assertEquals(amount, single[Account.composite_money.amount])
        assertEquals(currencyUnit, single[Account.composite_money.currency])
    }
}

class AccountDao(id: EntityID<Int>) : IntEntity(id) {

    val money: MonetaryAmount? by Account.composite_money

    val currency: CurrencyUnit? by Account.composite_money.currency

    val amount: BigDecimal? by Account.composite_money.amount

    companion object : EntityClass<Int, AccountDao>(Account)
}

object Account : IntIdTable("AccountTable") {

    val composite_money = compositeMoney(8, AMOUNT_SCALE, "composite_money").nullable()
}
