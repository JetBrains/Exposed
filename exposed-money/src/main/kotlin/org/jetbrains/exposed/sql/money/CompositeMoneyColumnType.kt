package org.jetbrains.exposed.sql.money

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ITable
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

fun ITable.compositeMoney(precision: Int, scale: Int, amountName: String, currencyName: String = amountName + "_C") =
        registerCompositeColumn(CompositeMoneyColumn(this, precision, scale, amountName, currencyName))


fun ITable.compositeMoney(amountColumn: Column<BigDecimal>, currencyColumn: Column<CurrencyUnit>): CompositeMoneyColumn<BigDecimal, CurrencyUnit, MonetaryAmount> {
    return CompositeMoneyColumn<BigDecimal, CurrencyUnit, MonetaryAmount>(amountColumn, currencyColumn).also {
        if (amountColumn !in columns && currencyColumn !in columns) {
            registerCompositeColumn(it)
        }
    }
}

@JvmName("compositeMoneyNullable")
fun ITable.compositeMoney(amountColumn: Column<BigDecimal?>, currencyColumn: Column<CurrencyUnit?>): CompositeMoneyColumn<BigDecimal?, CurrencyUnit?, MonetaryAmount?> {
    return CompositeMoneyColumn<BigDecimal?, CurrencyUnit?, MonetaryAmount?>(amountColumn, currencyColumn).also {
        if (amountColumn !in columns && currencyColumn !in columns) {
            registerCompositeColumn(it)
        }
    }
}