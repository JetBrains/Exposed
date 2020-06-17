package org.jetbrains.exposed.sql.money

import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount

/**
 * Represents amount of money and currency using Java Money API. Data are stored using two composite columns.
 *
 * @author Vladislav Kisel
 */

class CompositeMoneyColumn(val amount: Column<BigDecimal>, val currency: Column<CurrencyUnit>) : BiCompositeColumn<BigDecimal, CurrencyUnit, MonetaryAmount>(
        amount, currency,
        transformFromValue = { money ->
            val currencyValue = money.currency
            val amountValue = money.number.numberValue(BigDecimal::class.java)
            amountValue to currencyValue
        },
        transformToValue = { amountVal, currencyVal ->
            val result = Monetary.getDefaultAmountFactory().setNumber(amountVal as Number)

            if (currencyVal is CurrencyUnit) result.setCurrency(currencyVal)
            else if (currencyVal is String) result.setCurrency(currencyVal)

            result.create()
        }
) {
    constructor(table: Table, precision: Int, scale: Int, amountName: String, currencyName: String) : this(
            amount = Column(table, amountName, DecimalColumnType(precision, scale)),
            currency = Column(table, currencyName, CurrencyColumnType())
    )

}