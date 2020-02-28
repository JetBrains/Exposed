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
 *
 */
class Schema(private val name: String, val authorization: String? = null) {

    val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    val ddl: List<String>
        get() = createStatement()

    fun createStatement(): List<String> {

        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support create schema statement", currentDialect)
        }

        val createSchemaDDL = buildString {
            append("CREATE SCHEMA ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(identifier)

            if(TransactionManager.current().connection.metadata { supportsSchemasInDataManipulation }
                    || !TransactionManager.current().connection.metadata { supportsCatalogsInDataManipulation }) {
                appendIfNotNull(" AUTHORIZATION", authorization)
            }
        }

        return listOf(createSchemaDDL)
    }

    fun dropStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support drop schema statement", currentDialect)
        }

        val dropSchemaDDL = buildString {
            append("DROP SCHEMA ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(identifier)
        }

        return listOf(dropSchemaDDL)
    }

    fun setSchemaStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't schemas", currentDialect)
        }

        return listOf(currentDialect.setSchema(this))
    }
}
/** Appends both [str1] and [str2] to the receiver [StringBuilder] if [str2] is not `null`. */
fun StringBuilder.appendIfNotNull(str1: String, str2: Any?) = apply {
    if (str2 != null) {
        this.append("$str1 $str2")
    }
}