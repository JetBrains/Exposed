package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.jetbrains.exposed.v1.r2dbc.statements.asInt

// TODO add KDocs
interface R2dbcDatabaseConfig : DatabaseConfig {

    val dispatcher: CoroutineDispatcher

    val connectionFactoryOptions: ConnectionFactoryOptions

    val useExposedCodecs: Boolean

    val typeMapping: R2dbcTypeMapping

    val defaultR2dbcIsolationLevel: IsolationLevel?

    // TODO add KDocs
    class Builder : DatabaseConfig.Builder() {
        var useExposedCodecs: Boolean = true

        var connectionFactoryOptions: ConnectionFactoryOptions = ConnectionFactoryOptions.builder().build()

        var dispatcher: CoroutineDispatcher = Dispatchers.IO

        var typeMapping: R2dbcTypeMapping = R2dbcRegistryTypeMapping.default()

        var defaultR2dbcIsolationLevel: IsolationLevel? = null

        override var defaultIsolationLevel: Int
            get() = defaultR2dbcIsolationLevel?.asInt() ?: -1
            set(value) {
                error("Set a specific io.r2dbc.spi.IsolationLevel value directly to defaultR2dbcIsolationLevel instead")
            }

        fun setUrl(url: String) {
            connectionFactoryOptions { from(ConnectionFactoryOptions.parse(url)) }
        }

        fun connectionFactoryOptions(block: ConnectionFactoryOptions.Builder.() -> Unit) {
            val builder = ConnectionFactoryOptions.builder()
            connectionFactoryOptions.let { builder.from(it) }
            builder.apply(block)
            connectionFactoryOptions = builder.build()
        }

        fun build(): R2dbcDatabaseConfig {
            return object : R2dbcDatabaseConfig {
                override val sqlLogger: SqlLogger
                    get() = this@Builder.sqlLogger ?: Slf4jSqlDebugLogger
                override val useNestedTransactions: Boolean
                    get() = this@Builder.useNestedTransactions
                override val defaultFetchSize: Int?
                    get() = this@Builder.defaultFetchSize
                override val defaultIsolationLevel: Int
                    get() = this@Builder.defaultIsolationLevel
                override val defaultMaxAttempts: Int
                    get() = this@Builder.defaultMaxAttempts
                override val defaultMinRetryDelay: Long
                    get() = this@Builder.defaultMinRetryDelay
                override val defaultMaxRetryDelay: Long
                    get() = this@Builder.defaultMaxRetryDelay
                override val defaultReadOnly: Boolean
                    get() = this@Builder.defaultReadOnly
                override val warnLongQueriesDuration: Long?
                    get() = this@Builder.warnLongQueriesDuration
                override val maxEntitiesToStoreInCachePerEntity: Int
                    get() = this@Builder.maxEntitiesToStoreInCachePerEntity
                override val keepLoadedReferencesOutOfTransaction: Boolean
                    get() = this@Builder.keepLoadedReferencesOutOfTransaction
                override val explicitDialect: DatabaseDialect?
                    get() = this@Builder.explicitDialect
                override val defaultSchema: Schema?
                    get() = this@Builder.defaultSchema
                override val logTooMuchResultSetsThreshold: Int
                    get() = this@Builder.logTooMuchResultSetsThreshold

                @OptIn(ExperimentalKeywordApi::class)
                override val preserveKeywordCasing: Boolean
                    get() = this@Builder.preserveKeywordCasing

                override val dispatcher: CoroutineDispatcher
                    get() = this@Builder.dispatcher
                override val connectionFactoryOptions: ConnectionFactoryOptions
                    get() = this@Builder.connectionFactoryOptions
                override val useExposedCodecs: Boolean
                    get() = this@Builder.useExposedCodecs
                override val typeMapping: R2dbcTypeMapping
                    get() = this@Builder.typeMapping
                override val defaultR2dbcIsolationLevel: IsolationLevel?
                    get() = this@Builder.defaultR2dbcIsolationLevel
            }
        }
    }

    companion object {
        operator fun invoke(body: Builder.() -> Unit = {}): R2dbcDatabaseConfig = Builder().apply(body).build()
    }
}
