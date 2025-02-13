package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.transactions.CoreTransactionManager
import org.jetbrains.exposed.sql.vendors.ANSI_SQL_2003_KEYWORDS
import org.jetbrains.exposed.sql.vendors.VENDORS_KEYWORDS
import org.jetbrains.exposed.sql.vendors.currentDialect

/** Base class responsible for the parsing and processing of identifier tokens in SQL command syntax. */
abstract class IdentifierManagerApi {
    /** The string used to quote SQL identifiers for the database. */
    abstract val quoteString: String

    /** Whether the database treats mixed case unquoted identifiers as case-insensitive and stores them in upper case. */
    protected abstract val isUpperCaseIdentifiers: Boolean

    /** Whether the database treats mixed case quoted identifiers as case-insensitive and stores them in upper case. */
    protected abstract val isUpperCaseQuotedIdentifiers: Boolean

    /** Whether the database treats mixed case unquoted identifiers as case-insensitive and stores them in lower case. */
    protected abstract val isLowerCaseIdentifiers: Boolean

    /** Whether the database treats mixed case quoted identifiers as case-insensitive and stores them in lower case. */
    protected abstract val isLowerCaseQuotedIdentifiers: Boolean

    /** Whether the database treats and stores mixed case unquoted identifiers as case-sensitive. */
    protected abstract val supportsMixedIdentifiers: Boolean

    /** Whether the database treats and stores mixed case quoted identifiers as case-sensitive. */
    protected abstract val supportsMixedQuotedIdentifiers: Boolean

    /** Returns all keywords for the database beyond the [ANSI_SQL_2003_KEYWORDS]. */
    protected abstract fun dbKeywords(): List<String>

    /** All keywords for the database, including [ANSI_SQL_2003_KEYWORDS] and database-specific keywords. */
    val keywords by lazy {
        ANSI_SQL_2003_KEYWORDS + VENDORS_KEYWORDS[currentDialect.name].orEmpty() + dbKeywords()
    }

    /** The database-specific special characters that can be additionally used in unquoted identifiers. */
    protected abstract val extraNameCharacters: String

    /** The [OracleVersion] of the database, if Oracle is the underlying DBMS; otherwise, [OracleVersion.NonOracle]. */
    protected abstract val oracleVersion: OracleVersion

    /** The maximum number of characters in a column name allowed by the database. */
    protected abstract val maxColumnNameLength: Int

    /** Oracle version number classifier. */
    protected enum class OracleVersion { Oracle11g, Oracle12_1g, Oracle12plus, NonOracle }

    /** The maximum number of characters in an identifier allowed by the database. */
    protected val identifierLengthLimit by lazy {
        @Suppress("MagicNumber")
        when (oracleVersion) {
            OracleVersion.Oracle11g, OracleVersion.Oracle12_1g -> 30
            OracleVersion.Oracle12plus -> 128
            else -> maxColumnNameLength.takeIf { it > 0 } ?: Int.MAX_VALUE
        }
    }

    private val checkedIdentitiesCache = IdentifiersCache<Boolean>()
    private val checkedKeywordsCache = IdentifiersCache<Boolean>()
    private val shouldQuoteIdentifiersCache = IdentifiersCache<Boolean>()
    private val identifiersInProperCaseCache = IdentifiersCache<String>()
    private val quotedIdentifiersCache = IdentifiersCache<String>()

    private fun String.isIdentifier() = isNotEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    private fun String.isAKeyword(): Boolean = checkedKeywordsCache.getOrPut(lowercase()) {
        keywords.any { this.equals(it, true) }
    }

    @Deprecated(
        message = "This will be removed in future releases when the opt-out flag is removed in DatabaseConfig",
        level = DeprecationLevel.WARNING
    )
    private val shouldPreserveKeywordCasing by lazy {
        CoreTransactionManager.currentTransactionOrNull()?.db?.config?.preserveKeywordCasing == true
    }

    /** Returns whether an SQL token should be wrapped in quotations and caches the returned value. */
    fun needQuotes(identity: String): Boolean {
        return checkedIdentitiesCache.getOrPut(identity.lowercase()) {
            !identity.isAlreadyQuoted() && (identity.isAKeyword() || !identity.isIdentifier())
        }
    }

    private fun String.isAlreadyQuoted() = startsWith(quoteString) && endsWith(quoteString)

    /** Returns whether an [identity] should be wrapped in quotations and caches the returned value. */
    fun shouldQuoteIdentifier(identity: String): Boolean = shouldQuoteIdentifiersCache.getOrPut(identity) {
        val alreadyQuoted = identity.isAlreadyQuoted()
        val alreadyLower = identity == identity.lowercase()
        val alreadyUpper = identity == identity.uppercase()
        when {
            alreadyQuoted -> false
            identity.isAKeyword() && shouldPreserveKeywordCasing -> true
            supportsMixedIdentifiers -> false
            alreadyLower && isLowerCaseIdentifiers -> false
            alreadyUpper && isUpperCaseIdentifiers -> false
            oracleVersion != OracleVersion.NonOracle -> false
            supportsMixedQuotedIdentifiers && (!alreadyLower && !alreadyUpper) -> true
            else -> false
        }
    }

    /**
     * Returns an [identity] in a casing appropriate for its identifier status and the database,
     * then caches the returned value.
     */
    fun inProperCase(identity: String): String = identifiersInProperCaseCache.getOrPut(identity) {
        val alreadyQuoted = identity.isAlreadyQuoted()
        when {
            alreadyQuoted && supportsMixedQuotedIdentifiers -> identity
            alreadyQuoted && isUpperCaseQuotedIdentifiers -> identity.uppercase()
            alreadyQuoted && isLowerCaseQuotedIdentifiers -> identity.lowercase()
            supportsMixedIdentifiers -> identity
            identity.isAKeyword() && shouldPreserveKeywordCasing -> identity
            oracleVersion != OracleVersion.NonOracle -> identity.uppercase()
            isUpperCaseIdentifiers -> identity.uppercase()
            isLowerCaseIdentifiers -> identity.lowercase()
            else -> identity
        }
    }

    /** Returns an SQL token wrapped in quotations, if validated as necessary. */
    fun quoteIfNecessary(identity: String): String {
        return if (isDotPrefixedAndUnquoted(identity)) {
            identity.split('.').joinToString(".") { quoteTokenIfNecessary(it) }
        } else {
            quoteTokenIfNecessary(identity)
        }
    }

    /** Returns whether an [identity] is both unquoted and contains dot characters. */
    fun isDotPrefixedAndUnquoted(identity: String): Boolean = identity.contains('.') && !identity.isAlreadyQuoted()

    /** Returns an [identity] wrapped in quotations, if validated as necessary. */
    fun quoteIdentifierWhenWrongCaseOrNecessary(identity: String): String {
        val inProperCase = inProperCase(identity)
        return if (shouldQuoteIdentifier(identity) && inProperCase != identity) {
            quote(identity)
        } else {
            quoteIfNecessary(inProperCase)
        }
    }

    /** Returns an [identity] wrapped in quotations and containing no more than the maximum [identifierLengthLimit]. */
    fun cutIfNecessaryAndQuote(identity: String) = quoteIfNecessary(identity.take(identifierLengthLimit))

    private fun quoteTokenIfNecessary(token: String): String = if (needQuotes(token)) quote(token) else token

    private fun quote(identity: String) = quotedIdentifiersCache.getOrPut(identity) { "$quoteString$identity$quoteString".trim() }
}

private class IdentifiersCache<V : Any>(initialCapacity: Int = 100, private val cacheSize: Int = 1000) :
    java.util.LinkedHashMap<String, V>(initialCapacity) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size >= cacheSize
}
