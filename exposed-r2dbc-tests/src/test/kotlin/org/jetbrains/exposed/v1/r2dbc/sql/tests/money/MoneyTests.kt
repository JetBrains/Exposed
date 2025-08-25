package org.jetbrains.exposed.v1.r2dbc.sql.tests.money

import kotlinx.coroutines.flow.single
import org.javamoney.moneta.Money
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.money.compositeMoney
import org.jetbrains.exposed.v1.money.currency
import org.jetbrains.exposed.v1.money.nullable
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.Test
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.Monetary

private const val AMOUNT_SCALE = 5

open class MoneyBaseTest : R2dbcDatabaseTestsBase() {

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
        withTables(Account) {
            expectException<ExposedR2dbcException> {
                Account.insertAndGetId {
                    it[composite_money] = toInsert
                }
            }

            expectException<ExposedR2dbcException> {
                Account.insertAndGetId {
                    it[composite_money.amount] = amount
                    it[composite_money.currency] = toInsert.currency
                }
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

    private suspend fun R2dbcTransaction.assertInsertOfCompositeValueReturnsEquivalentOnSelect(toInsert: Money?) {
        val accountID = Account.insertAndGetId {
            it[composite_money] = toInsert
        }

        val single = Account.select(Account.composite_money).where { Account.id.eq(accountID) }.single()
        val inserted = single[Account.composite_money]

        assertEquals(toInsert, inserted)
    }

    private suspend fun R2dbcTransaction.assertInsertOfComponentValuesReturnsEquivalentOnSelect(toInsert: Money?) {
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

object Account : IntIdTable("AccountTable") {

    val composite_money = compositeMoney(8, AMOUNT_SCALE, "composite_money").nullable()
}
