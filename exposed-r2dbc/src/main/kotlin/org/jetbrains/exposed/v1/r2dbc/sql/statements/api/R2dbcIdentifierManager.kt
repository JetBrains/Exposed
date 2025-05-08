package org.jetbrains.exposed.v1.r2dbc.sql.statements.api

import io.r2dbc.spi.ConnectionMetadata
import org.jetbrains.exposed.v1.r2dbc.sql.vendors.metadata.MetadataProvider
import org.jetbrains.exposed.v1.sql.statements.api.IdentifierManagerApi

/**
 * Class responsible for the parsing and processing of identifier tokens in SQL command syntax.
 */
internal class R2dbcIdentifierManager(
    metadata: MetadataProvider,
    connectionData: ConnectionMetadata
) : IdentifierManagerApi() {
    override val quoteString = metadata.propertyProvider.identifierQuoteString.trim()

    override val isUpperCaseIdentifiers = metadata.propertyProvider.storesUpperCaseIdentifiers

    override val isUpperCaseQuotedIdentifiers = metadata.propertyProvider.storesUpperCaseQuotedIdentifiers

    override val isLowerCaseIdentifiers = metadata.propertyProvider.storesLowerCaseIdentifiers

    override val isLowerCaseQuotedIdentifiers = metadata.propertyProvider.storesLowerCaseQuotedIdentifiers

    override val supportsMixedIdentifiers = metadata.propertyProvider.supportsMixedCaseIdentifiers

    override val supportsMixedQuotedIdentifiers = metadata.propertyProvider.supportsMixedCaseQuotedIdentifiers

    private val _keywords = metadata.propertyProvider.sqlKeywords().split(',')

    override fun dbKeywords(): List<String> = _keywords

    override val extraNameCharacters = metadata.propertyProvider.extraNameCharacters

    @Suppress("MagicNumber")
    override val oracleVersion = when {
        connectionData.databaseProductName != "Oracle" -> OracleVersion.NonOracle
        else -> OracleVersion.Oracle12plus
    }

    override val maxColumnNameLength: Int = metadata.propertyProvider.maxColumnNameLength
}
