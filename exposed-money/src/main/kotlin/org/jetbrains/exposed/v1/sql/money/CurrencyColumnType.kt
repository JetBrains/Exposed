package org.jetbrains.exposed.v1.sql.money

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import javax.money.CurrencyUnit
import javax.money.Monetary

/**
 * Varchar column for storing currency (JSR354 [CurrencyUnit])
 *
 * @author Vladislav Kisel
 */
@Suppress("MagicNumber")
class CurrencyColumnType : ColumnType<CurrencyUnit>() {

    override fun sqlType(): String = currentDialect.dataTypeProvider.varcharType(COLUMN_LENGTH)

    override fun validateValueBeforeUpdate(value: CurrencyUnit?) {
        if (value is CurrencyUnit) {
            val valueLength = value.currencyCode.codePointCount(0, value.currencyCode.length)
            require(valueLength <= COLUMN_LENGTH) {
                "Value can't be stored to database column because exceeds length ($valueLength > $COLUMN_LENGTH)"
            }
        }
    }

    override fun valueFromDB(value: Any): CurrencyUnit {
        return when (value) {
            is CurrencyUnit -> value
            is String -> Monetary.getCurrency(value)
            else -> valueFromDB(value.toString())
        }
    }

    override fun notNullValueToDB(value: CurrencyUnit): Any = value.currencyCode

    override fun nonNullValueToString(value: CurrencyUnit): String = buildString {
        append('\'')
        append(escape(value.currencyCode))
        append('\'')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as VarCharColumnType

        return COLUMN_LENGTH == other.colLength
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + COLUMN_LENGTH
        return result
    }

    private fun escape(value: String): String = value.map { charactersToEscape[it] ?: it }.joinToString("")

    companion object {
        private const val COLUMN_LENGTH = 3

        private val charactersToEscape = mapOf(
            '\'' to "\'\'",
            '\r' to "\\r",
            '\n' to "\\n"
        )
    }
}

/** Creates a character column, with the specified [name], for storing currency (as javax.money.CurrencyUnit). */
fun Table.currency(name: String): Column<CurrencyUnit> = registerColumn(name, CurrencyColumnType())
