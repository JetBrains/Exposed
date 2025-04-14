package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.DatabaseMetaData

/**
 * Class responsible for the parsing and processing of identifier tokens in SQL command syntax.
 */
internal class JdbcIdentifierManager(metadata: DatabaseMetaData) : IdentifierManagerApi() {
    override val quoteString = metadata.identifierQuoteString!!.trim()
    override val isUpperCaseIdentifiers = metadata.storesUpperCaseIdentifiers()
    override val isUpperCaseQuotedIdentifiers = metadata.storesUpperCaseQuotedIdentifiers()
    override val isLowerCaseIdentifiers = metadata.storesLowerCaseIdentifiers()
    override val isLowerCaseQuotedIdentifiers = metadata.storesLowerCaseQuotedIdentifiers()
    override val supportsMixedIdentifiers = metadata.supportsMixedCaseIdentifiers()
    override val supportsMixedQuotedIdentifiers = metadata.supportsMixedCaseQuotedIdentifiers()
    private val _keywords = metadata.sqlKeywords.split(',')
    override fun dbKeywords(): List<String> = _keywords
    override val extraNameCharacters = metadata.extraNameCharacters!!

    @Suppress("MagicNumber")
    override val oracleVersion = when {
        metadata.databaseProductName != "Oracle" -> OracleVersion.NonOracle
        metadata.databaseMajorVersion <= 11 -> OracleVersion.Oracle11g
        metadata.databaseMajorVersion == 12 && metadata.databaseMinorVersion == 1 -> OracleVersion.Oracle12_1g
        else -> OracleVersion.Oracle12plus
    }
    override val maxColumnNameLength: Int = metadata.maxColumnNameLength
}

// TODO could we make it public for internal API and remove duplication functions like org.jetbrains.exposed.sql.vendors.DatabaseDialectKt.inProperCase
internal fun String.inProperCase(): String =
    TransactionManager.currentOrNull()?.db?.identifierManager?.inProperCase(this@inProperCase) ?: this
