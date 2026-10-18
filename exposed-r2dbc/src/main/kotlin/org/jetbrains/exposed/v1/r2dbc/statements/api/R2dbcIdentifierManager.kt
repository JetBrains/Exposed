package org.jetbrains.exposed.v1.r2dbc.statements.api

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.r2dbc.vendors.metadata.MetadataProvider

/**
 * Class responsible for the parsing and processing of identifier tokens in SQL command syntax.
 *
 * All values are resolved from the hard-coded [metadata] of the vendor dialect, so no connection is required.
 */
internal class R2dbcIdentifierManager(
    metadata: MetadataProvider,
    isOracle: Boolean
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

    override val oracleVersion = if (isOracle) OracleVersion.Oracle12plus else OracleVersion.NonOracle

    override val maxColumnNameLength: Int = metadata.propertyProvider.maxColumnNameLength
}
