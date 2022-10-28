package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.DatabaseDialect

@Suppress("LongParameterList")
class DatabaseConfig private constructor(
    val sqlLogger: SqlLogger,
    val useNestedTransactions: Boolean,
    val defaultFetchSize: Int?,
    val defaultIsolationLevel: Int,
    val defaultRepetitionAttempts: Int,
    val defaultReadOnly: Boolean,
    val warnLongQueriesDuration: Long?,
    val maxEntitiesToStoreInCachePerEntity: Int,
    val keepLoadedReferencesOutOfTransaction: Boolean,
    val explicitDialect: DatabaseDialect?,
    val defaultSchema: Schema?,
    val logTooMuchResultSetsThreshold: Int
) {

    class Builder(
        /**
         * SQLLogger to be used to log all SQL statements. [Slf4jSqlDebugLogger] by default
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
         * Default transaction isolation level. If not specified database-specific level will be used
         * Can be overridden on per-transaction level by specifying `transactionIsolation` parameter of `transaction` function
         * Check [Database.getDefaultIsolationLevel] for the database defaults
         */
        var defaultIsolationLevel: Int = -1,
        /**
         * How many retries will be made inside any `transaction` block if SQLException happens
         * Can be overridden on per-transaction level by specifying `repetitionAttempts` parameter on call
         * Default attempts are 3
         */
        var defaultRepetitionAttempts: Int = 3,

        /**
         * Should all connections/transactions be executed in read-only mode by default or not
         * Default state is false
         */
        var defaultReadOnly: Boolean = false,
        /**
         * Threshold in milliseconds to log queries which exceed the threshold with WARN level
         * No tracing enabled by default
         * Can be set on per-transaction level by setting [Transaction.warnLongQueriesDuration] field
         */
        var warnLongQueriesDuration: Long? = null,
        /**
         * Amount of entities to keep in an EntityCache per an Entity class
         * Applicable only when `exposed-dao` module is used
         * Can be overridden on per-transaction basis via [EntityCache.maxEntitiesToStore]
         * All entities will be kept by default
         */
        var maxEntitiesToStoreInCachePerEntity: Int = Int.MAX_VALUE,
        /**
         * Turns on "mode" for Exposed DAO to store relations (after they were loaded)
         * within the entity that will allow to access them outside the transaction.
         * Useful when [eager loading](https://github.com/JetBrains/Exposed/wiki/DAO#eager-loading) is used
         */
        var keepLoadedReferencesOutOfTransaction: Boolean = false,

        /**
         * Set the explicit dialect for a database. Can be useful when working with not supported dialects which have the same behavior as the one that Exposed supports
         */
        var explicitDialect: DatabaseDialect? = null,

        /**
         * Set the default schema for a database.
         */
        var defaultSchema: Schema? = null,

        /**
         * Log too much result sets opened in parallel.
         * The error log will contain the stacktrace of the place in the code where new result set occurs, and it exceeds the threshold.
         * 0 value means no log needed
         */
        var logTooMuchResultSetsThreshold: Int = 0,
    )

    companion object {
        operator fun invoke(body: Builder.() -> Unit = {}): DatabaseConfig {
            val builder = Builder().apply(body)
            return DatabaseConfig(
                sqlLogger = builder.sqlLogger ?: Slf4jSqlDebugLogger,
                useNestedTransactions = builder.useNestedTransactions,
                defaultFetchSize = builder.defaultFetchSize,
                defaultIsolationLevel = builder.defaultIsolationLevel,
                defaultRepetitionAttempts = builder.defaultRepetitionAttempts,
                defaultReadOnly = builder.defaultReadOnly,
                warnLongQueriesDuration = builder.warnLongQueriesDuration,
                maxEntitiesToStoreInCachePerEntity = builder.maxEntitiesToStoreInCachePerEntity,
                keepLoadedReferencesOutOfTransaction = builder.keepLoadedReferencesOutOfTransaction,
                explicitDialect = builder.explicitDialect,
                defaultSchema = builder.defaultSchema,
                logTooMuchResultSetsThreshold = builder.logTooMuchResultSetsThreshold
            )
        }
    }
}
