package org.jetbrains.exposed.sql.money

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

/**
 * Creates a composite column made up of:
 * - A numeric column, with the specified [amountName], for storing numbers with the specified [precision] and [scale].
 * - A character column, with the specified [currencyName], for storing currency (as javax.money.CurrencyUnit).
 *
 * @sample org.jetbrains.exposed.sql.money.MoneyDefaultsTest.TableWithDBDefault
 */
fun Table.compositeMoney(precision: Int, scale: Int, amountName: String, currencyName: String = amountName + "_C") =
    registerCompositeColumn(CompositeMoneyColumn(this, precision, scale, amountName, currencyName))

/** Creates a composite column made up of a decimal column and a currency column. */
fun Table.compositeMoney(
    amountColumn: Column<BigDecimal>,
    currencyColumn: Column<CurrencyUnit>
): CompositeMoneyColumn<BigDecimal, CurrencyUnit, MonetaryAmount> {
    return CompositeMoneyColumn<BigDecimal, CurrencyUnit, MonetaryAmount>(amountColumn, currencyColumn).also {
        if (amountColumn !in columns && currencyColumn !in columns) {
            registerCompositeColumn(it)
        }
    }
}

/** Creates a composite column made up of a nullable decimal column and a nullable currency column. */
@JvmName("compositeMoneyNullable")
fun Table.compositeMoney(
    amountColumn: Column<BigDecimal?>,
    currencyColumn: Column<CurrencyUnit?>
): CompositeMoneyColumn<BigDecimal?, CurrencyUnit?, MonetaryAmount?> {
    return CompositeMoneyColumn<BigDecimal?, CurrencyUnit?, MonetaryAmount?>(amountColumn, currencyColumn).also {
        if (amountColumn !in columns && currencyColumn !in columns) {
            registerCompositeColumn(it)
        }
    }
}
