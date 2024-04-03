package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.DatabaseDialect

/**
 * A configuration class for a [Database].
 *
 * Parameters set in this class apply to all transactions that use the [Database] instance,
 * unless an applicable override is specified in an individual transaction block.
 */
@Suppress("LongParameterList")
class DatabaseConfig private constructor(
    val sqlLogger: SqlLogger,
    val useNestedTransactions: Boolean,
    val defaultFetchSize: Int?,
    val defaultIsolationLevel: Int,
    val defaultMaxAttempts: Int,
    val defaultMinRetryDelay: Long,
    val defaultMaxRetryDelay: Long,
    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxAttempts"),
        level = DeprecationLevel.WARNING
    )
    val defaultRepetitionAttempts: Int,
    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMinRetryDelay"),
        level = DeprecationLevel.WARNING
    )
    val defaultMinRepetitionDelay: Long,
    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxRetryDelay"),
        level = DeprecationLevel.WARNING
    )
    val defaultMaxRepetitionDelay: Long,
    val defaultReadOnly: Boolean,
    val warnLongQueriesDuration: Long?,
    val maxEntitiesToStoreInCachePerEntity: Int,
    val keepLoadedReferencesOutOfTransaction: Boolean,
    val explicitDialect: DatabaseDialect?,
    val defaultSchema: Schema?,
    val logTooMuchResultSetsThreshold: Int,
    val preserveKeywordCasing: Boolean,
) {

    class Builder(
        /**
         * SQLLogger to be used to log all SQL statements. [Slf4jSqlDebugLogger] by default.
         */
        var sqlLogger: SqlLogger? = null,
        /**
         * Turn on/off nested transactions support. Is disabled by default
         */
        var useNestedTransactions: Boolean = false,
        /**
         * How many records will be fetched at once by select queries
         */
        var defaultFetchSize: Int? = null,
        /**
         * Default transaction isolation level. If not specified, the database-specific level will be used.
         * This can be overridden on a per-transaction level by specifying the `transactionIsolation` parameter of
         * the `transaction` function.
         * Check [Database.getDefaultIsolationLevel] for the database defaults.
         */
        var defaultIsolationLevel: Int = -1,
        /**
         * The maximum amount of attempts that will be made to perform any transaction block.
         * If this value is set to 1 and an SQLException happens, the exception will be thrown without performing a retry.
         * This can be overridden on a per-transaction level by specifying the `maxAttempts` property in a
         * `transaction` block.
         * Default amount of attempts is 3.
         */
        var defaultMaxAttempts: Int = 3,
        /**
         * The minimum number of milliseconds to wait before retrying a transaction if an SQLException happens.
         * This can be overridden on a per-transaction level by specifying the `minRetryDelay` property in a
         * `transaction` block.
         * Default minimum delay is 0.
         */
        var defaultMinRetryDelay: Long = 0,
        /**
         * The maximum number of milliseconds to wait before retrying a transaction if an SQLException happens.
         * This can be overridden on a per-transaction level by specifying the `maxRetryDelay` property in a
         * `transaction` block.
         * Default maximum delay is 0.
         */
        var defaultMaxRetryDelay: Long = 0,
        @Deprecated(
            message = "This property will be removed in future releases",
            replaceWith = ReplaceWith("defaultMaxAttempts"),
            level = DeprecationLevel.WARNING
        )
        var defaultRepetitionAttempts: Int = 3,
        @Deprecated(
            message = "This property will be removed in future releases",
            replaceWith = ReplaceWith("defaultMinRetryDelay"),
            level = DeprecationLevel.WARNING
        )
        var defaultMinRepetitionDelay: Long = 0,
        @Deprecated(
            message = "This property will be removed in future releases",
            replaceWith = ReplaceWith("defaultMaxRetryDelay"),
            level = DeprecationLevel.WARNING
        )
        var defaultMaxRepetitionDelay: Long = 0,
        /**
         * Should all connections/transactions be executed in read-only mode by default or not.
         * Default state is false.
         */
        var defaultReadOnly: Boolean = false,
        /**
         * Threshold in milliseconds to log queries which exceed the threshold with WARN level.
         * No tracing enabled by default.
         * This can be set on a per-transaction level by setting [Transaction.warnLongQueriesDuration] field.
         */
        var warnLongQueriesDuration: Long? = null,
        /**
         * Amount of entities to keep in an EntityCache per an Entity class.
         * Applicable only when `exposed-dao` module is used.
         * This can be overridden on a per-transaction basis via [EntityCache.maxEntitiesToStore].
         * All entities will be kept by default.
         */
        var maxEntitiesToStoreInCachePerEntity: Int = Int.MAX_VALUE,
        /**
         * Turns on "mode" for Exposed DAO to store relations (after they were loaded) within the entity that will
         * allow access to them outside the transaction.
         * Useful when [eager loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading) is used.
         */
        var keepLoadedReferencesOutOfTransaction: Boolean = false,
        /**
         * Set the explicit dialect for a database.
         * This can be useful when working with unsupported dialects which have the same behavior as the one that
         * Exposed supports.
         */
        var explicitDialect: DatabaseDialect? = null,
        /**
         * Set the default schema for a database.
         */
        var defaultSchema: Schema? = null,
        /**
         * Log too much result sets opened in parallel.
         * The error log will contain the stacktrace of the place in the code where a new result set occurs, and it
         * exceeds the threshold.
         * 0 value means no log needed.
         */
        var logTooMuchResultSetsThreshold: Int = 0,
        /**
         * Toggle whether table and column identifiers that are also keywords should retain their case sensitivity.
         * Keeping user-defined case sensitivity (value set to `true`) is the default setting.
         */
        @ExperimentalKeywordApi
        var preserveKeywordCasing: Boolean = true,
    )

    companion object {
        operator fun invoke(body: Builder.() -> Unit = {}): DatabaseConfig {
            val builder = Builder().apply(body)
            @OptIn(ExperimentalKeywordApi::class)
            return DatabaseConfig(
                sqlLogger = builder.sqlLogger ?: Slf4jSqlDebugLogger,
                useNestedTransactions = builder.useNestedTransactions,
                defaultFetchSize = builder.defaultFetchSize,
                defaultIsolationLevel = builder.defaultIsolationLevel,
                defaultMaxAttempts = builder.defaultMaxAttempts.coerceAtLeast(1),
                defaultMinRetryDelay = builder.defaultMinRetryDelay,
                defaultMaxRetryDelay = builder.defaultMaxRetryDelay,
                defaultRepetitionAttempts = builder.defaultRepetitionAttempts,
                defaultMinRepetitionDelay = builder.defaultMinRepetitionDelay,
                defaultMaxRepetitionDelay = builder.defaultMaxRepetitionDelay,
                defaultReadOnly = builder.defaultReadOnly,
                warnLongQueriesDuration = builder.warnLongQueriesDuration,
                maxEntitiesToStoreInCachePerEntity = builder.maxEntitiesToStoreInCachePerEntity,
                keepLoadedReferencesOutOfTransaction = builder.keepLoadedReferencesOutOfTransaction,
                explicitDialect = builder.explicitDialect,
                defaultSchema = builder.defaultSchema,
                logTooMuchResultSetsThreshold = builder.logTooMuchResultSetsThreshold,
                preserveKeywordCasing = builder.preserveKeywordCasing,
            )
        }
    }
}
