package org.jetbrains.exposed.sql

const val DEFAULT_REPETITION_ATTEMPTS = 3

class DatabaseConfig private constructor(
    val sqlLogger: SqlLogger,
    val useNestedTransactions: Boolean,
    val defaultFetchSize: Int?,
    val defaultIsolationLevel: Int,
    val defaultRepetitionAttempts: Int,
    val warnLongQueriesDuration: Long?,
) {

    class Builder(
        var sqlLogger: SqlLogger? = null,
        var useNestedTransactions: Boolean? = null,
        var defaultFetchSize: Int? = null,
        var defaultIsolationLevel: Int = -1,
        var defaultRepetitionAttempts: Int = DEFAULT_REPETITION_ATTEMPTS,
        var warnLongQueriesDuration: Long? = null
    )

    companion object {
        operator fun invoke(body: Builder.() -> Unit = {}): DatabaseConfig {
            val builder = Builder().apply(body)
            return DatabaseConfig(
                sqlLogger = builder.sqlLogger ?: Slf4jSqlDebugLogger,
                useNestedTransactions = builder.useNestedTransactions ?: false,
                defaultFetchSize = builder.defaultFetchSize,
                defaultIsolationLevel = builder.defaultIsolationLevel,
                defaultRepetitionAttempts = builder.defaultRepetitionAttempts,
                warnLongQueriesDuration = builder.warnLongQueriesDuration
            )
        }
    }
}
