package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import java.sql.DatabaseMetaData

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
    override val oracleVersion = when {
        metadata.databaseProductName != "Oracle" -> OracleVersion.NonOracle
        metadata.databaseMajorVersion <= 11 -> OracleVersion.Oracle11g
        else -> OracleVersion.Oracle12plus
    }
    override val maxColumnNameLength: Int = metadata.maxColumnNameLength
}
