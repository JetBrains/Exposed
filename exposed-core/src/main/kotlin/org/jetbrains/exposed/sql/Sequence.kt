package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.lang.StringBuilder

/**
 * Sequence : an object that generates a sequence of numeric values.
 *
 * @param name          The name of the sequence
 * @param startWith     The first sequence number to be generated.
 * @param incrementBy   The interval between sequence numbers.
 * @param minValue      The minimum value of the sequence.
 * @param maxValue      The maximum value of the sequence.
 * @param cycle         Indicates that the sequence continues to generate values after reaching either its maximum or minimum value.
 * @param cache         Number of values of the sequence the database preallocates and keeps in memory for faster access.
 */
class Sequence(private val name: String,
                    val startWith: Int? = null,
                    val incrementBy: Int? = null,
                    val minValue: Int? = null,
                    val maxValue: Int? = null,
                    val cycle: Boolean? = null,
                    val cache: Int? = null) {

    val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    val ddl: List<String>
        get() = createStatement()

    fun createStatement(): List<String> {
        if (!currentDialect.supportsCreateSequence ) {
            throw UnsupportedByDialectException("The current dialect doesn't support create sequence statement", currentDialect)
        }

        val createTableDDL = buildString {
            append("CREATE SEQUENCE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(identifier)
            appendIfNotNull(" START WITH", startWith)
            appendIfNotNull(" INCREMENT BY", incrementBy)
            appendIfNotNull(" MINVALUE", minValue)
            appendIfNotNull(" MAXVALUE", maxValue)

            if (cycle == true) {
                append(" CYCLE")
            }

            appendIfNotNull(" CACHE", cache)
        }

        return listOf(createTableDDL)
    }

    fun dropStatement() = listOf("DROP SEQUENCE $identifier")

    fun StringBuilder.appendIfNotNull(str: String, strToCheck: Any?) = apply {
        if (strToCheck != null) {
            this.append("$str $strToCheck")
        }
    }

}
