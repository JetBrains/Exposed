package org.jetbrains.exposed.v1.plugin.core.tables

private val IDENTIFIER_SEPARATORS: Regex = Regex("[^A-Za-z0-9]+")

/**
 * Converts a database table name to the PascalCase name of the generated Kotlin `object`.
 *
 * For example, `USERS` becomes `Users`, `user_accounts` becomes `UserAccounts`, and `userAccounts` is left as `UserAccounts`.
 */
internal fun String.toObjectName(): String = identifierTokens().joinToString("") { it.capitalized() }.asIdentifier()

/**
 * Converts a database column name to the camelCase name of the generated Kotlin property.
 *
 * For example, `FIRST_NAME` becomes `firstName`, `ID` becomes `id`, and `firstName` is left unchanged.
 */
internal fun String.toPropertyName(): String = identifierTokens()
    .mapIndexed { index, token -> if (index == 0) token.decapitalized() else token.capitalized() }
    .joinToString("")
    .asIdentifier()

/**
 * Splits a database identifier into name tokens. Separators are any non-alphanumeric characters,
 * so the quote characters of a pre-quoted identifier such as `"reserved"` are dropped as well.
 * A single token that is entirely upper case is lower-cased, so that `FIRST` becomes `first`.
 * Mixed-case tokens keep their inner casing.
 */
private fun String.identifierTokens(): List<String> = split(IDENTIFIER_SEPARATORS)
    .filter { it.isNotEmpty() }
    .map { token -> if (token.all { !it.isLetter() || it.isUpperCase() }) token.lowercase() else token }

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

private fun String.decapitalized(): String = replaceFirstChar { it.lowercaseChar() }

/** Ensures the name is a valid Kotlin identifier by prefixing an underscore when it starts with a digit or is empty. */
private fun String.asIdentifier(): String = if (isEmpty() || first().isDigit()) "_$this" else this
