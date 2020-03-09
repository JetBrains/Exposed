package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.vendors.ANSI_SQL_2003_KEYWORDS
import org.jetbrains.exposed.sql.vendors.VENDORS_KEYWORDS
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.util.*

abstract class IdentifierManagerApi {
    abstract val quoteString : String
    protected abstract val isUpperCaseIdentifiers : Boolean
    protected abstract val isUpperCaseQuotedIdentifiers : Boolean
    protected abstract val isLowerCaseIdentifiers : Boolean
    protected abstract val isLowerCaseQuotedIdentifiers : Boolean
    protected abstract val supportsMixedIdentifiers : Boolean
    protected abstract val supportsMixedQuotedIdentifiers : Boolean
    protected abstract fun dbKeywords() : List<String>
    val keywords by lazy { ANSI_SQL_2003_KEYWORDS + VENDORS_KEYWORDS[currentDialect.name].orEmpty() + dbKeywords() }
    protected abstract val extraNameCharacters : String
    protected abstract val oracleVersion : OracleVersion
    protected abstract val maxColumnNameLength : Int

    protected enum class OracleVersion { Oracle11g, Oracle12plus, NonOracle }

    protected val identifierLengthLimit by lazy {
        when(oracleVersion) {
            OracleVersion.Oracle11g -> 30
            OracleVersion.Oracle12plus -> 128
            else -> maxColumnNameLength.takeIf { it > 0 } ?: Int.MAX_VALUE
        }
    }

    val checkedIdentities = object : LinkedHashMap<String, Boolean>(100) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size >= 1000
    }

    private fun String.isIdentifier() = !isEmpty() && first().isIdentifierStart() && all { it.isIdentifierStart() || it in '0'..'9' }
    private fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_' || this in extraNameCharacters

    fun needQuotes (identity: String) : Boolean {
        return checkedIdentities.getOrPut(identity.toLowerCase()) {
            !identity.isAlreadyQuoted() && (keywords.any { identity.equals(it, true) } || !identity.isIdentifier())
        }
    }

    private fun String.isAlreadyQuoted()  = startsWith(quoteString) && endsWith(quoteString)

    fun shouldQuoteIdentifier(identity: String) : Boolean {
        val alreadyQuoted = identity.isAlreadyQuoted()
        val alreadyLower = identity == identity.toLowerCase()
        val alreadyUpper = identity == identity.toUpperCase()
        return when {
            alreadyQuoted -> false
            supportsMixedIdentifiers -> false
            alreadyLower && isLowerCaseIdentifiers -> false
            alreadyUpper && isUpperCaseIdentifiers -> false
            oracleVersion != OracleVersion.NonOracle -> false
            supportsMixedQuotedIdentifiers && (!alreadyLower && !alreadyUpper) -> true
            else -> false
        }
    }

    fun inProperCase(identity: String) : String {
        val alreadyQuoted = identity.isAlreadyQuoted()
        return when {
            alreadyQuoted && supportsMixedQuotedIdentifiers -> identity
            alreadyQuoted && isUpperCaseQuotedIdentifiers -> identity.toUpperCase()
            alreadyQuoted && isLowerCaseQuotedIdentifiers -> identity.toLowerCase()
            supportsMixedIdentifiers -> identity
            oracleVersion != OracleVersion.NonOracle -> identity.toUpperCase()
            isUpperCaseIdentifiers -> identity.toUpperCase()
            isLowerCaseIdentifiers -> identity.toLowerCase()
            else -> identity
        }
    }

    fun quoteIfNecessary (identity: String) : String {
        return if (identity.contains('.'))
            identity.split('.').joinToString(".") {quoteTokenIfNecessary(it)}
        else {
            quoteTokenIfNecessary(identity)
        }
    }

    fun quoteIdentifierWhenWrongCaseOrNecessary(identity: String) : String {
        val inProperCase = inProperCase(identity)
        return if (shouldQuoteIdentifier(identity) && inProperCase != identity)
            quote(identity)
        else
            quoteIfNecessary(inProperCase)
    }

    fun cutIfNecessaryAndQuote(identity: String) = quoteIfNecessary(identity.take(identifierLengthLimit))

    private fun quoteTokenIfNecessary(token: String) : String = if (needQuotes(token)) quote(token) else token

    private fun quote(identity: String) = "$quoteString$identity$quoteString".trim()
}