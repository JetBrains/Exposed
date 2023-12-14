package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.lang.StringBuilder

/**
 * Represents a database schema.
 *
 * @param name The schema name.
 * @param authorization Specifies the name of the database-level principal that will own the schema.
 * @param password Used only for Oracle schema.
 * @param defaultTablespace Used only for Oracle schema.
 * @param temporaryTablespace Used only for Oracle schema.
 * @param quota Used only for Oracle schema.
 * @param on Used only for Oracle schema.
 */
data class Schema(
    private val name: String,
    val authorization: String? = null,
    val password: String? = null,
    val defaultTablespace: String? = null,
    val temporaryTablespace: String? = null,
    val quota: String? = null,
    val on: String? = null
) {
    /** This schema's name in proper database casing. */
    val identifier
        get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    /** The SQL statements that create this schema. */
    val ddl: List<String>
        get() = createStatement()

    /**
     * Checks if this schema exists or not.
     */
    fun exists(): Boolean = currentDialect.schemaExists(this)

    /** Returns the SQL statements that create this schema. */
    fun createStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema) {
            throw UnsupportedByDialectException("The current dialect doesn't support create schema statement", currentDialect)
        }

        return listOf(currentDialect.createSchema(this))
    }

    /** Returns the SQL statements that drop this schema, as well as all its objects if [cascade] is `true`. */
    fun dropStatement(cascade: Boolean): List<String> {
        if (!currentDialect.supportsCreateSchema) {
            throw UnsupportedByDialectException("The current dialect doesn't support drop schema statement", currentDialect)
        }

        return listOf(currentDialect.dropSchema(this, cascade))
    }

    /** Returns the SQL statements that set this schema as the current schema. */
    fun setSchemaStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema) {
            throw UnsupportedByDialectException("The current dialect doesn't support schemas", currentDialect)
        }

        return listOf(currentDialect.setSchema(this))
    }
}

/** Appends both [str1] and [str2] to the receiver [StringBuilder] if [str2] is not `null`. */
internal fun StringBuilder.appendIfNotNull(str1: String, str2: Any?) = apply {
    if (str2 != null) {
        this.append("$str1 $str2")
    }
}
