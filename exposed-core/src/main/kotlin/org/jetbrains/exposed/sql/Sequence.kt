package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.lang.StringBuilder

/**
 * Database Sequence.
 *
 * @param name          Name of the sequence.
 * @param startWith     Beginning of the sequence.
 * @param incrementBy   Value is added to the current sequence value to create a new value.
 * @param minValue      Minimum value a sequence can generate.
 * @param maxValue      Maximum value for the sequence.
 * @param cycle         Allows the sequence to wrap around when the [maxValue] or [minValue] has been reached by an ascending or descending sequence respectively.
 * @param cache         Specifies how many sequence numbers are to be preallocated and stored in memory for faster access.
 */
class Sequence(private val name: String,
                    val startWith: Long? = null,
                    val incrementBy: Long? = null,
                    val minValue: Long? = null,
                    val maxValue: Long? = null,
                    val cycle: Boolean? = null,
                    val cache: Long? = null) {

    val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    val ddl: List<String>
        get() = createStatement()

    /**
     * Returns the SQL command that creates sequence with the specified properties.
     */
    fun createStatement(): List<String> {
        if (!currentDialect.supportsCreateSequence ) {
            throw UnsupportedByDialectException("The current dialect doesn't support create sequence statement", currentDialect)
        }

        val createSequenceDDL = buildString {
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

        return listOf(createSequenceDDL)
    }

    fun dropStatement(): List<String> {
        if (!currentDialect.supportsCreateSequence) {
            throw UnsupportedByDialectException("The current dialect doesn't support drop sequence statement", currentDialect)
        }

        val dropSequenceDDL = buildString {
            append("DROP SEQUENCE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(identifier)
        }

        return listOf(dropSequenceDDL)
    }
}
