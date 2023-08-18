package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.vendors.ANSI_SQL_2003_KEYWORDS
import org.jetbrains.exposed.sql.vendors.VENDORS_KEYWORDS
import org.jetbrains.exposed.sql.vendors.currentDialect

abstract class IdentifierManagerApi {
    abstract val quoteString: String
    protected abstract val isUpperCaseIdentifiers: Boolean
    protected abstract val isUpperCaseQuotedIdentifiers: Boolean
    protected abstract val isLowerCaseIdentifiers: Boolean
    protected abstract val isLowerCaseQuotedIdentifiers: Boolean
    protected abstract val supportsMixedIdentifiers: Boolean
    protected abstract val supportsMixedQuotedIdentifiers: Boolean
    protected abstract fun dbKeywords(): List<String>
    val keywords by lazy { ANSI_SQL_2003_KEYWORDS + VENDORS_KEYWORDS[currentDialect.name].orEmpty() + dbKeywords() }
    protected abstract val extraNameCharacters: String
    protected abstract val oracleVersion: OracleVersion
    protected abstract val maxColumnNameLength: Int

    protected enum class OracleVersion { Oracle11g, Oracle12_1g, Oracle12plus, NonOracle }

    protected val identifierLengthLimit by lazy {
        @Suppress("MagicNumber")
        when (oracleVersion) {
            OracleVersion.Oracle11g, OracleVersion.Oracle12_1g -> 30
            OracleVersion.Oracle12plus -> 128
            else -> maxColumnNameLength.takeIf { it > 0 } ?: Int.MAX_VALUE
        }
    }

    private val checkedIdentitiesCache = IdentifiersCache<Boolean>()
    private val shouldQuoteIdentifiersCache = IdentifiersCache<Boolean>()
    private val identifiersInProperCaseCache = IdentifiersCache<String>()
    private val quotedIdentifiersCache = IdentifiersCache<String>()

    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    fun needQuotes(identity: String): Boolean {
        return checkedIdentitiesCache.getOrPut(identity.lowercase()) {
            !identity.isAlreadyQuoted() && (keywords.any { identity.equals(it, true) } || !identity.isIdentifier())
        }
    }

    private fun String.isAlreadyQuoted() = startsWith(quoteString) && endsWith(quoteString)

    fun shouldQuoteIdentifier(identity: String): Boolean = shouldQuoteIdentifiersCache.getOrPut(identity) {
        val alreadyQuoted = identity.isAlreadyQuoted()
        val alreadyLower = identity == identity.lowercase()
        val alreadyUpper = identity == identity.uppercase()
        when {
            alreadyQuoted -> false
            supportsMixedIdentifiers -> false
            alreadyLower && isLowerCaseIdentifiers -> false
            alreadyUpper && isUpperCaseIdentifiers -> false
            oracleVersion != OracleVersion.NonOracle -> false
            supportsMixedQuotedIdentifiers && (!alreadyLower && !alreadyUpper) -> true
            else -> false
        }
    }

    fun inProperCase(identity: String): String = identifiersInProperCaseCache.getOrPut(identity) {
        val alreadyQuoted = identity.isAlreadyQuoted()
        when {
            alreadyQuoted && supportsMixedQuotedIdentifiers -> identity
            alreadyQuoted && isUpperCaseQuotedIdentifiers -> identity.uppercase()
            alreadyQuoted && isLowerCaseQuotedIdentifiers -> identity.lowercase()
            supportsMixedIdentifiers || keywords.any { identity.equals(it, true) } -> identity
            oracleVersion != OracleVersion.NonOracle -> identity.uppercase()
            isUpperCaseIdentifiers -> identity.uppercase()
            isLowerCaseIdentifiers -> identity.lowercase()
            else -> identity
        }
    }

    fun quoteIfNecessary(identity: String): String {
        return if (identity.contains('.')) {
            identity.split('.').joinToString(".") { quoteTokenIfNecessary(it) }
        } else {
            quoteTokenIfNecessary(identity)
        }
    }

    fun quoteIdentifierWhenWrongCaseOrNecessary(identity: String): String {
        val inProperCase = inProperCase(identity)
        return if (shouldQuoteIdentifier(identity) && inProperCase != identity) {
            quote(identity)
        } else {
            quoteIfNecessary(inProperCase)
        }
    }

    fun cutIfNecessaryAndQuote(identity: String) = quoteIfNecessary(identity.take(identifierLengthLimit))

    private fun quoteTokenIfNecessary(token: String): String = if (needQuotes(token)) quote(token) else token

    private fun quote(identity: String) = quotedIdentifiersCache.getOrPut(identity) { "$quoteString$identity$quoteString".trim() }
}

private class IdentifiersCache<V : Any>(initialCapacity: Int = 100, private val cacheSize: Int = 1000) :
    java.util.LinkedHashMap<String, V>(initialCapacity) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size >= cacheSize
}
