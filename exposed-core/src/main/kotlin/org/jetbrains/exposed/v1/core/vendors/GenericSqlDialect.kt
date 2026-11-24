package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi

/**
 * Identifier manager with standard SQL defaults, used when rendering identifiers outside an active
 * transaction.
 *
 * Identifiers keep the casing they were declared with and are wrapped in quotations only when
 * required by standard SQL rules, meaning when they are reserved keywords or contain characters
 * that are invalid in unquoted identifiers.
 */
internal object GenericIdentifierManager : IdentifierManagerApi() {
    override val quoteString = "\""
    override val isUpperCaseIdentifiers = false
    override val isUpperCaseQuotedIdentifiers = false
    override val isLowerCaseIdentifiers = false
    override val isLowerCaseQuotedIdentifiers = false
    override val supportsMixedIdentifiers = true
    override val supportsMixedQuotedIdentifiers = true
    override fun dbKeywords(): List<String> = emptyList()
    override val extraNameCharacters = ""

    @Suppress("MagicNumber")
    override val oracleVersion = OracleVersion.NonOracle

    @Suppress("MagicNumber")
    override val maxColumnNameLength: Int = Int.MAX_VALUE
}

/**
 * A dialect-independent fallback for [currentDialect], used when an expression is rendered outside
 * an active transaction and no explicit dialect has been set.
 *
 * Rendering then follows generic SQL rules: identifiers are handled by [GenericIdentifierManager],
 * while data type and function rendering is delegated to [H2Dialect], whose behavior is the closest
 * to standard SQL among the supported vendors.
 *
 * **Note:** SQL generated through this fallback is suitable for display, logging, and testing
 * purposes. When executed against a specific database, statements should be rendered within a
 * transaction so that database-specific syntax is used.
 */
internal object GenericSqlDialect : DatabaseDialect by H2Dialect() {
    override val name: String = "generic"

    // H2Dialect resolves its providers through the compatibility mode of the transaction's
    // database, so the fallback uses the original generic providers directly instead.
    override val dataTypeProvider: DataTypeProvider = H2DataTypeProvider
    override val functionProvider: FunctionProvider = H2FunctionProvider

    override fun toString(): String = "GenericSqlDialect"
}
