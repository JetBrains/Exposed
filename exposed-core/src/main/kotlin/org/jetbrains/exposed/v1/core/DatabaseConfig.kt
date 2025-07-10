package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect

// TODO instead of magic number? put back into DatabaseConfig?
internal const val DEFAULT_MAX_ATTEMPTS = 3

/**
 * Base configuration for a [DatabaseApi] instance.
 *
 * Parameters set in this class apply to all transactions that use the [DatabaseApi] instance,
 * unless an applicable override is specified in an individual transaction block.
 */
interface DatabaseConfig {
    val sqlLogger: SqlLogger
    val useNestedTransactions: Boolean
    val defaultFetchSize: Int?
    val defaultIsolationLevel: Int
    val defaultMaxAttempts: Int
    val defaultMinRetryDelay: Long
    val defaultMaxRetryDelay: Long
    val defaultReadOnly: Boolean
    val warnLongQueriesDuration: Long?
    val maxEntitiesToStoreInCachePerEntity: Int
    val keepLoadedReferencesOutOfTransaction: Boolean
    val explicitDialect: DatabaseDialect?
    val defaultSchema: Schema?
    val logTooMuchResultSetsThreshold: Int
    val preserveKeywordCasing: Boolean

    /**
     * Builder API responsible for constructing a custom [DatabaseApi] configuration parameter state.
     */
    open class Builder {
        /**
         * SQLLogger to be used to log all SQL statements. [Slf4jSqlDebugLogger] by default.
         */
        var sqlLogger: SqlLogger? = null

        /**
         * Turn on/off nested transactions support. Is disabled by default
         */
        var useNestedTransactions: Boolean = false

        /**
         * How many records will be fetched at once by select queries
         */
        var defaultFetchSize: Int? = null

        /**
         * Default transaction isolation level. If not specified, the database-specific level will be used.
         * This can be overridden on a per-transaction level by specifying the `transactionIsolation` parameter of
         * the `transaction` function.
         *
         * Check `Database.getDefaultIsolationLevel()` for the database defaults.
         *
         * If using Exposed with an R2DBC driver, `defaultR2dbcIsolationLevel` should be used directly instead.
         */
        open var defaultIsolationLevel: Int = -1

        /**
         * The maximum amount of attempts that will be made to perform any transaction block.
         * If this value is set to 1 and a database exception happens, the exception will be thrown without performing a retry.
         * This can be overridden on a per-transaction level by specifying the `maxAttempts` property in a
         * `transaction` block.
         * Default amount of attempts is 3.
         *
         * @throws IllegalArgumentException If the amount of attempts is set to a value less than 1.
         */
        var defaultMaxAttempts: Int = DEFAULT_MAX_ATTEMPTS

        /**
         * The minimum number of milliseconds to wait before retrying a transaction if a database exception happens.
         * This can be overridden on a per-transaction level by specifying the `minRetryDelay` property in a
         * `transaction` block.
         * Default minimum delay is 0.
         */
        var defaultMinRetryDelay: Long = 0

        /**
         * The maximum number of milliseconds to wait before retrying a transaction if a database exception happens.
         * This can be overridden on a per-transaction level by specifying the `maxRetryDelay` property in a
         * `transaction` block.
         * Default maximum delay is 0.
         */
        var defaultMaxRetryDelay: Long = 0

        /**
         * Should all connections/transactions be executed in read-only mode by default or not.
         * Default state is false.
         */
        var defaultReadOnly: Boolean = false

        /**
         * Threshold in milliseconds to log queries which exceed the threshold with WARN level.
         * No tracing enabled by default.
         * This can be set on a per-transaction level by setting [Transaction.warnLongQueriesDuration] field.
         */
        var warnLongQueriesDuration: Long? = null

        /**
         * Amount of entities to keep in an EntityCache per an Entity class.
         * Applicable only when `exposed-dao` module is used.
         * This can be overridden on a per-transaction basis via `EntityCache.maxEntitiesToStore`.
         * All entities will be kept by default.
         */
        var maxEntitiesToStoreInCachePerEntity: Int = Int.MAX_VALUE

        /**
         * Turns on "mode" for Exposed DAO to store relations (after they were loaded) within the entity that will
         * allow access to them outside the transaction.
         * Useful when [eager loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading) is used.
         */
        var keepLoadedReferencesOutOfTransaction: Boolean = false

        /**
         * Set the explicit dialect for a database.
         * This can be useful when working with unsupported dialects which have the same behavior as the one that
         * Exposed supports.
         */
        var explicitDialect: DatabaseDialect? = null

        /**
         * Set the default schema for a database.
         */
        var defaultSchema: Schema? = null

        /**
         * Log too much result sets opened in parallel.
         * The error log will contain the stacktrace of the place in the code where a new result set occurs, and it
         * exceeds the threshold.
         * 0 value means no log needed.
         */
        var logTooMuchResultSetsThreshold: Int = 0

        /**
         * Toggle whether table and column identifiers that are also keywords should retain their case sensitivity.
         * Keeping user-defined case sensitivity (value set to `true`) is the default setting.
         */
        @ExperimentalKeywordApi
        var preserveKeywordCasing: Boolean = true
    }

    companion object {
        // TODO make sure R2dbcDatabaseConfig has constructor function so that it is compatible with JDBC
        operator fun invoke(body: Builder.() -> Unit = {}): DatabaseConfig {
            val builder = Builder().apply(body)
            require(builder.defaultMaxAttempts > 0) { "defaultMaxAttempts must be set to perform at least 1 attempt." }

            // TODO make default implementation to simplify & call constructor func instead
            return object : DatabaseConfig {
                override val sqlLogger: SqlLogger
                    get() = builder.sqlLogger ?: Slf4jSqlDebugLogger
                override val useNestedTransactions: Boolean
                    get() = builder.useNestedTransactions
                override val defaultFetchSize: Int?
                    get() = builder.defaultFetchSize
                override val defaultIsolationLevel: Int
                    get() = builder.defaultIsolationLevel
                override val defaultMaxAttempts: Int
                    get() = builder.defaultMaxAttempts
                override val defaultMinRetryDelay: Long
                    get() = builder.defaultMinRetryDelay
                override val defaultMaxRetryDelay: Long
                    get() = builder.defaultMaxRetryDelay
                override val defaultReadOnly: Boolean
                    get() = builder.defaultReadOnly
                override val warnLongQueriesDuration: Long?
                    get() = builder.warnLongQueriesDuration
                override val maxEntitiesToStoreInCachePerEntity: Int
                    get() = builder.maxEntitiesToStoreInCachePerEntity
                override val keepLoadedReferencesOutOfTransaction: Boolean
                    get() = builder.keepLoadedReferencesOutOfTransaction
                override val explicitDialect: DatabaseDialect?
                    get() = builder.explicitDialect
                override val defaultSchema: Schema?
                    get() = builder.defaultSchema
                override val logTooMuchResultSetsThreshold: Int
                    get() = builder.logTooMuchResultSetsThreshold

                @OptIn(ExperimentalKeywordApi::class)
                override val preserveKeywordCasing: Boolean
                    get() = builder.preserveKeywordCasing
            }
        }
    }
}
