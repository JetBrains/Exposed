package org.jetbrains.exposed.sql.money

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal
import javax.money.Monetary
import javax.money.MonetaryAmount

const val CURRENCY_CODE_LENGTH = 3

class MoneyColumnType(
        /** Total count of digits in the whole number and its fractional part. */
        private val length: Int
) : ColumnType() {

    /**
     * Money is represented as a string: big decimal plus currency code
     */
    override fun sqlType(): String {
        return "VARCHAR(${length + CURRENCY_CODE_LENGTH})"
    }

    override fun notNullValueToDB(value: Any): Any {
        return when (value) {
            is String -> value
            is MonetaryAmount -> value.currency.currencyCode + value.number.toString()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }
    }

    override fun nonNullValueToString(value: Any): String = buildString {
        append('\'')
        append(notNullValueToDB(value) as String)
        append('\'')
    }

    override fun valueFromDB(value: Any): Any {
        return when (value) {
            is MonetaryAmount -> value
            is String -> {
                val currencyCode = extractCurrencyCode(value)
                val amount = BigDecimal(value.removePrefix(currencyCode).trim())

                Monetary.getDefaultAmountFactory()
                        .setNumber(amount)
                        .setCurrency(currencyCode)
                        .create()
            }
            else -> valueFromDB(value.toString())
        }
    }

    /**
     * Currency code is stored as 3 chars prefix
     */
    private fun extractCurrencyCode(value: String): String = value.substring(0, CURRENCY_CODE_LENGTH)

}

/**
 * A date column to store a date.
 *
 * @param name The column name
 * @param length Total count of digits in the whole number and its fractional part
 */
fun Table.money(name: String, length: Int): Column<MonetaryAmount> = registerColumn(name, MoneyColumnType(length))
