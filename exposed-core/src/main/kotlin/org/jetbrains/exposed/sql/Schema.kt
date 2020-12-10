package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.lang.StringBuilder

/**
 * Database Schema
 *
 * @param name the schema name
 * @param authorization owner_name Specifies the name of the database-level
 * principal that will own the schema.
 * @param password used only for oracle schema.
 * @param defaultTablespace used only for oracle schema.
 * @param temporaryTablespace used only for oracle schema.
 * @param quota used only for oracle schema.
 * @param on used only for oracle schema.
 *
 *
 */
data class Schema(private val name: String,
             val authorization: String? = null,
             val password: String? = null,
             val defaultTablespace: String? = null,
             val temporaryTablespace: String? = null,
             val quota: String? = null,
             val on: String? = null) {

    val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    val ddl: List<String>
        get() = createStatement()

    /**
     * Checks if this schema exists or not.
     */
    fun exists(): Boolean = currentDialect.schemaExists(this)

    fun createStatement(): List<String> {

        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support create schema statement", currentDialect)
        }

        return listOf(currentDialect.createSchema(this))
    }

    fun dropStatement(cascade: Boolean): List<String> {
        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support drop schema statement", currentDialect)
        }

        return listOf(currentDialect.dropSchema(this, cascade))
    }

    fun setSchemaStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema ) {
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
