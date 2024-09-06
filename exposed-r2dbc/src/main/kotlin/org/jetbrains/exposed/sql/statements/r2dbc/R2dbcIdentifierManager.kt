package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.ConnectionMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.vendors.MetadataProvider

internal class R2dbcIdentifierManager(
    metadata: MetadataProvider,
    connectionData: ConnectionMetadata
) : IdentifierManagerApi() {
    override val quoteString = metadata.identifierQuoteString().trim()

    override val isUpperCaseIdentifiers = metadata.storesUpperCaseIdentifiers()

    override val isUpperCaseQuotedIdentifiers = metadata.storesUpperCaseQuotedIdentifiers()

    override val isLowerCaseIdentifiers = metadata.storesLowerCaseIdentifiers()

    override val isLowerCaseQuotedIdentifiers = metadata.storesLowerCaseQuotedIdentifiers()

    override val supportsMixedIdentifiers = metadata.supportsMixedCaseIdentifiers()

    override val supportsMixedQuotedIdentifiers = metadata.supportsMixedCaseQuotedIdentifiers()

    private val _keywords = metadata.sqlKeywords().split(',')

    override fun dbKeywords(): List<String> = _keywords

    override val extraNameCharacters = metadata.extraNameCharacters()

    @Suppress("MagicNumber")
    override val oracleVersion = when {
        connectionData.databaseProductName != "Oracle" -> OracleVersion.NonOracle
        else -> OracleVersion.Oracle12plus
    }

    override val maxColumnNameLength: Int = metadata.maxColumnNameLength()
}
