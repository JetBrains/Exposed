package org.jetbrains.exposed.dao.r2dbc.tests.money

import kotlinx.coroutines.flow.toList
import org.javamoney.moneta.Money
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.money.compositeMoney
import org.jetbrains.exposed.v1.money.nullable
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

private const val AMOUNT_SCALE = 5

class MoneyTests : R2dbcDatabaseTestsBase() {
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
                val found = AccountDao.find { it }.toList()

                assertEquals(1, found.count())
                val next = found.iterator().next()
                assertEquals(money, next.money)
                assertEquals(money.currency, next.currency)
                assertEquals(BigDecimal.TEN.setScale(AMOUNT_SCALE), next.amount)
            }
        }
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
