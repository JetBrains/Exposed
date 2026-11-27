package org.jetbrains.exposed.v1.plugin.core.tables.schema

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi

/**
 * Returns [name] as reported by the database, but altered to a form that Exposed must use to reference the same object.
 */
internal fun IdentifierManagerApi.toExposedIdentifier(name: String): String = if (isInDefaultCase(name)) {
    name
} else {
    "$quoteString$name$quoteString"
}

/** Whether [name] is already in the database's default identifier case, so that it can be used without quotes. */
internal fun IdentifierManagerApi.isInDefaultCase(name: String): Boolean = inProperCase(name) == name
