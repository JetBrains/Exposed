package org.jetbrains.exposed.v1.sql.money

import org.jetbrains.exposed.v1.sql.BiCompositeColumn
import org.jetbrains.exposed.v1.sql.Column
import org.jetbrains.exposed.v1.sql.DecimalColumnType
import org.jetbrains.exposed.v1.sql.Table
import java.math.BigDecimal
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount

/**
 * Represents amount of money and currency using Java Money API. Data is stored using two composite columns.
 *
 * @author Vladislav Kisel
 */
class CompositeMoneyColumn<T1 : BigDecimal?, T2 : CurrencyUnit?, R : MonetaryAmount?>(
    val amount: Column<T1>,
    val currency: Column<T2>,
    nullable: Boolean = false
) :
    BiCompositeColumn<T1, T2, R>(
        column1 = amount,
        column2 = currency,
        transformFromValue = { money ->
            val amountValue = money?.number?.numberValue(BigDecimal::class.java) as? T1
            val currencyValue = money?.currency as? T2
            amountValue to currencyValue
        },
        transformToValue = { amountVal, currencyVal ->
            if (amountVal == null || currencyVal == null) {
                null as R
            } else {
                val result = Monetary.getDefaultAmountFactory().setNumber(amountVal as Number)

                when (currencyVal) {
                    is CurrencyUnit -> result.setCurrency(currencyVal)
                    is String -> result.setCurrency(currencyVal)
                }

                result.create() as R
            }
        },
        nullable
    )

/**
 * Creates a composite column made up of a numeric column, with the specified [amountName], for storing numbers with
 * the specified [precision] and [scale], as wel as a character column, with the specified [currencyName], for storing
 * currency (as javax.money.CurrencyUnit).
 */
@Suppress("FunctionNaming")
fun CompositeMoneyColumn(table: Table, precision: Int, scale: Int, amountName: String, currencyName: String) =
    CompositeMoneyColumn<BigDecimal, CurrencyUnit, MonetaryAmount>(
        amount = Column(table, amountName, DecimalColumnType(precision, scale)),
        currency = Column(table, currencyName, CurrencyColumnType())
    )

/** Marks this [CompositeMoneyColumn] as nullable. */
@Suppress("UNCHECKED_CAST")
fun <T1 : BigDecimal, T2 : CurrencyUnit, R : MonetaryAmount> CompositeMoneyColumn<T1, T2, R>.nullable(): CompositeMoneyColumn<T1?, T2?, R?> {
    return with(amount.table) {
        (this@nullable as BiCompositeColumn<T1, T2, R>).nullable() as CompositeMoneyColumn<T1?, T2?, R?>
    }
}
