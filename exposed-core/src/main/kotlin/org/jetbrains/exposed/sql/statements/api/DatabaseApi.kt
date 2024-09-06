package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.annotations.TestOnly
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.name
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class representing the underlying database to which connections are made
 * and on which transaction tasks are performed.
 */
abstract class DatabaseApi(
    val config: DatabaseConfig,
    val connector: () -> ExposedConnection<*>
) {
    /** The connection URL for the database. */
    abstract val url: String

    /** The name of the database based on the name of the underlying JDBC driver. */
    abstract val vendor: String

    /** The version number of the database as a [BigDecimal]. */
    abstract val version: BigDecimal

    /** Whether the database supports ALTER TABLE with an add column clause. */
    abstract val supportsAlterTableWithAddColumn: Boolean

    /** Whether the database supports ALTER TABLE with a drop column clause. */
    abstract val supportsAlterTableWithDropColumn: Boolean

    /** Whether the database supports getting multiple result sets from a single execute. */
    abstract val supportsMultipleResultSets: Boolean

    /** The database-specific class responsible for parsing and processing identifier tokens in SQL syntax. */
    abstract val identifierManager: IdentifierManagerApi

    abstract fun <T> metadata(body: ExposedDatabaseMetadata.() -> T): T

    /** The name of the database as a [DatabaseDialect]. */
    val dialect: DatabaseDialect by lazy {
        config.explicitDialect
            ?: dialects[vendor.lowercase()]?.invoke()
            ?: error("No dialect registered for $name. URL=$url")
    }

    /** Whether nested transaction blocks are configured to act like top-level transactions. */
    var useNestedTransactions: Boolean = config.useNestedTransactions
        @Deprecated("Use DatabaseConfig to define the useNestedTransactions", level = DeprecationLevel.ERROR)
        @TestOnly
        set

    /** The default number of results that should be fetched when queries are executed. */
    var defaultFetchSize: Int? = config.defaultFetchSize
        private set

    @Deprecated("Use DatabaseConfig to define the defaultFetchSize", level = DeprecationLevel.ERROR)
    @TestOnly
    fun defaultFetchSize(size: Int): DatabaseApi {
        defaultFetchSize = size
        return this
    }

    /** Whether the version number of the database is equal to or greater than the provided [version]. */
    fun isVersionCovers(version: BigDecimal) = this.version >= version

    companion object {
        internal val dialects = ConcurrentHashMap<String, () -> DatabaseDialect>()
    }
}
