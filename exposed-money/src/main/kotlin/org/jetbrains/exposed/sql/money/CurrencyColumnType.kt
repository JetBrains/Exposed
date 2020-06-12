package org.jetbrains.exposed.sql.money

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import javax.money.CurrencyUnit
import javax.money.Monetary

/**
 * Varchar column for storing currency (JSR354 [CurrencyUnit])
 *
 * @author Vladislav Kisel
 */
class CurrencyColumnType : VarCharColumnType(3) {

    override fun notNullValueToDB(value: Any): Any {
        return when (value) {
            is String -> value
            is CurrencyUnit -> value.currencyCode
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }
    }

    override fun valueFromDB(value: Any): Any {
        return when (value) {
            is CurrencyUnit -> value
            is String -> Monetary.getCurrency(value)
            else -> valueFromDB(value.toString())
        }
    }

}

fun Table.currency(name: String): Column<CurrencyUnit> = registerColumn(name, CurrencyColumnType())
