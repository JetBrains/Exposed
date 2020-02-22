package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect

/**
 * Database Schema
 *
 * @param name the schema name
 * @param authorization owner_name Specifies the name of the database-level
 * principal that will own the schema.
 *
 */
class Schema(private val name: String, private val authorization: String? = null) {

    val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)

    val ddl: List<String>
        get() = createStatement()

    fun createStatement(): List<String> {

        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support create schema statement", currentDialect)
        }

        val createTableDDL = buildString {
            append("CREATE SCHEMA ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(identifier)

            if(!TransactionManager.current().connection.metadata { supportsSchemasInDataManipulation }
                    && TransactionManager.current().connection.metadata { supportsCatalogsInDataManipulation }) {
                appendIfNotNull(" AUTHORIZATION", authorization)
            }
        }

        return listOf(createTableDDL)
    }

    fun dropStatement(): List<String> {
        if (!currentDialect.supportsCreateSchema ) {
            throw UnsupportedByDialectException("The current dialect doesn't support drop schema statement", currentDialect)
        }

        val dropSequenceDDL = buildString {
            append("DROP SCHEMA ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(identifier)
        }

        return listOf(dropSequenceDDL)
    }
}
