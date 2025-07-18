package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ReferenceOption

/**
 * Base class responsible for shared utility methods needed for retrieving and storing information about
 * the underlying driver and associated [database].
 */
abstract class ExposedDatabaseMetadata(val database: String) {
    /** Clears and resets any stored information about the database's current schema to default values. */
    abstract fun resetCurrentScheme()

    @Suppress("ForbiddenComment")
    // TODO: THIS should become protected after the usage in DatabaseDialect is fully deprecated
    /**
     * Returns the corresponding [ReferenceOption] for the specified [refOption] result,
     * or `null` if the database result is an invalid string without a corresponding match.
     */
    @InternalApi
    abstract fun resolveReferenceOption(refOption: String): ReferenceOption?

    /** Clears any cached values. */
    abstract fun cleanCache()

    /** The database-specific and metadata-reliant implementation of [IdentifierManagerApi]. */
    abstract val identifierManager: IdentifierManagerApi
}
