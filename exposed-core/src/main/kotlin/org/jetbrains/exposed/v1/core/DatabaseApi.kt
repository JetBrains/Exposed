package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class representing the underlying database to which connections are made
 * and on which transaction tasks are performed.
 */
abstract class DatabaseApi protected constructor(
    protected val resolvedVendor: String? = null,
    val config: DatabaseConfig
) {
    /** Whether nested transaction blocks are configured to act like top-level transactions. */
    val useNestedTransactions: Boolean = config.useNestedTransactions

    override fun toString(): String =
        "ExposedDatabase[${hashCode()}]($resolvedVendor${config.explicitDialect?.let { ", dialect=$it" } ?: ""})"

    /** The connection URL for the database. */
    abstract val url: String

    /** The name of the database based on the name of the underlying JDBC driver. */
    abstract val vendor: String

    /** The name of the database as a [DatabaseDialect]. */
    abstract val dialect: DatabaseDialect

    /** The mode of the database. This currently only applies to H2 databases. */
    abstract val dialectMode: String?

    /** The version number of the database as a [BigDecimal]. */
    abstract val version: BigDecimal

    /** Whether the version number of the database is equal to or greater than the provided [version]. */
    abstract fun isVersionCovers(version: BigDecimal): Boolean

    /** Whether the version number of the database is equal to or greater than the provided [majorVersion] and [minorVersion]. */
    abstract fun isVersionCovers(majorVersion: Int, minorVersion: Int): Boolean

    /** The full version number of the database as a String. */
    abstract val fullVersion: String

    /** Whether the database supports ALTER TABLE with an add column clause. */
    abstract val supportsAlterTableWithAddColumn: Boolean

    /** Whether the database supports ALTER TABLE with a drop column clause. */
    abstract val supportsAlterTableWithDropColumn: Boolean

    /** Whether the database supports getting multiple result sets from a single execute. */
    abstract val supportsMultipleResultSets: Boolean

    /** The database-specific class responsible for parsing and processing identifier tokens in SQL syntax. */
    abstract val identifierManager: IdentifierManagerApi

    /** The default number of results that should be fetched when queries are executed. */
    var defaultFetchSize: Int? = config.defaultFetchSize
        private set

    companion object {
        // TODO Assess whether concurrent hash map is actually needed
        @InternalApi // how to avoid this
        val dialects = ConcurrentHashMap<String, () -> DatabaseDialect>()

        /** Registers a new [DatabaseDialect] with the identifier [prefix]. */
        fun registerDialect(prefix: String, dialect: () -> DatabaseDialect) {
            @OptIn(InternalApi::class)
            dialects[prefix.lowercase()] = dialect
        }
    }
}
